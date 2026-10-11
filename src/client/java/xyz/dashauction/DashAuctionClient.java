package xyz.dashauction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/** GUI plus game-notification listener. Does not send commands, deliver items, or refund. */
public final class DashAuctionClient implements ClientModInitializer {
    public static final AuctionEngine AUCTION = new AuctionEngine();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("dashauction-payment-format.txt");
    private static String formatText = PaymentTemplate.DEFAULT;
    private static PaymentTemplate paymentFormat = new PaymentTemplate(formatText);
    private static Object serverConnection;
    private static boolean endAnnounced;

    @Override public void onInitializeClient() {
        try {
            if (Files.isRegularFile(CONFIG)) {
                String saved = Files.readString(CONFIG).strip();
                paymentFormat = new PaymentTemplate(saved);
                formatText = saved;
            }
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[DashAuction] Invalid saved payment format: " + error);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("dashauction")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new AuctionScreen()));
                    return 1;
                })
                .then(literal("preview").then(argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        Optional<PaymentTemplate.Payment> test = paymentFormat.parse(
                            StringArgumentType.getString(ctx, "message"));
                        tell(test.isPresent()
                            ? "Template matches " + test.get().player() + " " + Money.format(test.get().amount())
                            : "Template did not match. Edit format in GUI.");
                        return 1;
                    })))
        ));
        // Only server GAME messages count; a player's forged chat line cannot become a bid.
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> {
            Minecraft mc = Minecraft.getInstance();
            if (overlay || mc.getConnection() != serverConnection || AUCTION.phase() != AuctionEngine.Phase.LIVE) return;
            long receivedNanos = System.nanoTime();
            Optional<PaymentTemplate.Payment> found = paymentFormat.parse(text.getString());
            if (found.isEmpty()) return;
            PaymentTemplate.Payment payment = found.get();
            AuctionEngine.Result result = AUCTION.payment(payment.player(), payment.amount(), receivedNanos);
            if (result == AuctionEngine.Result.NEW_LEADER)
                tell("New highest payment: " + payment.player() + " - " + Money.format(payment.amount()));
            else if (result == AuctionEngine.Result.LATE) finish();
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (AUCTION.phase() != AuctionEngine.Phase.LIVE) return;
            if (mc.getConnection() != serverConnection) {
                AUCTION.cancel();
                tell("Auction cancelled: disconnected or switched servers.");
            } else if (AUCTION.tick(System.nanoTime())) finish();
        });
    }

    public static String formatText() { return formatText; }

    public static void start(String itemId, BigInteger worth, BigInteger min, int seconds, String paymentTemplate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) throw new IllegalArgumentException("Join a server before starting");
        PaymentTemplate validated = new PaymentTemplate(paymentTemplate);
        AUCTION.start(itemId, worth, min, seconds, System.nanoTime());
        paymentFormat = validated;
        formatText = paymentTemplate;
        serverConnection = mc.getConnection();
        endAnnounced = false;
        try {
            Files.writeString(CONFIG, paymentTemplate, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException error) {
            System.err.println("[DashAuction] Could not save format: " + error);
        }
        tell("Auction started! Watching incoming payments until the timer ends.");
    }

    private static void finish() {
        if (endAnnounced) return;
        endAnnounced = true;
        AuctionEngine.Leader leader = AUCTION.leader();
        tell(leader == null ? "Auction ended: No qualifying payment." :
            "Auction ended: Winner " + leader.name() + " - " + Money.format(leader.amount()));
        tell("No item delivery or refunds are performed.");
    }
    private static void tell(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[DashAuction] " + line), false);
        });
    }
}
