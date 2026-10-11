package xyz.dashauction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Observes incoming game notifications for the active client connection.
 * No automated transfers/refunds/items. Does not listen to player-authored chat.
 */
public final class DashAuctionClient implements ClientModInitializer {
    public static final AuctionEngine AUCTION = new AuctionEngine();
    private static final Path FORMAT_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("dashauction-payment-format.txt");
    private static String formatText = PaymentTemplate.DEFAULT;
    private static PaymentTemplate paymentFormat = new PaymentTemplate(formatText);
    private static Object serverConnection;
    private static boolean endedAnnounced;
    private static long winnerDisplayUntil;

    @Override public void onInitializeClient() {
        try {
            if (Files.isRegularFile(FORMAT_FILE)) {
                String format = Files.readString(FORMAT_FILE).strip();
                paymentFormat = new PaymentTemplate(format);
                formatText = format;
            }
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[DashAuction] Invalid saved payment format: " + error);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
            ClientCommands.literal("dashauction")
                .executes(ctx -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.gui.setScreen(new AuctionScreen()));
                    return 1;
                })
                .then(ClientCommands.literal("preview")
                    .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String example = StringArgumentType.getString(ctx, "message");
                            Optional<PaymentTemplate.Payment> parsed = paymentFormat.parse(example);
                            ctx.getSource().sendFeedback(Component.literal(parsed.isPresent()
                                ? "Payment format matches " + parsed.get().player() + ": "
                                    + Money.format(parsed.get().amount())
                                : "Format did not match example. Configure exact server text in the GUI."));
                            return 1;
                        })))
        ));
        AuctionHud.register();

        // SERVER game messages only. Ordinary player chat can be spoofed and is ignored.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            Minecraft client = Minecraft.getInstance();
            if (overlay || client.getConnection() != serverConnection
                || AUCTION.phase() != AuctionEngine.Phase.LIVE) return;
            long receivedAt = System.nanoTime();
            Optional<PaymentTemplate.Payment> found = paymentFormat.parse(message.getString());
            if (found.isEmpty()) return;
            PaymentTemplate.Payment payment = found.get();
            AuctionEngine.Result result = AUCTION.payment(payment.player(), payment.amount(), receivedAt);
            if (result == AuctionEngine.Result.NEW_LEADER) {
                AuctionEngine.Leader leader = AUCTION.leader();
                tell("NEW LEADER: " + leader.name() + " - " + Money.format(leader.total()) + " total");
            } else if (result == AuctionEngine.Result.LATE) {
                end();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (AUCTION.phase() != AuctionEngine.Phase.LIVE) return;
            if (client.getConnection() != serverConnection) {
                AUCTION.cancel();
                winnerDisplayUntil = 0;
                tell("Auction cancelled on disconnect/server change.");
            } else if (AUCTION.tick(System.nanoTime())) {
                end();
            }
        });
    }

    public static String formatText() { return formatText; }
    public static boolean showResult() {
        return AUCTION.phase() == AuctionEngine.Phase.ENDED && System.nanoTime() < winnerDisplayUntil;
    }

    public static void start(String item, BigInteger worth, int seconds, String format) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null)
            throw new IllegalArgumentException("Connect to the server before starting");
        PaymentTemplate validated = new PaymentTemplate(format);
        AUCTION.start(item, worth, seconds, System.nanoTime());
        paymentFormat = validated;
        formatText = format;
        serverConnection = client.getConnection();
        endedAnnounced = false;
        winnerDisplayUntil = 0;
        try {
            Files.createDirectories(FORMAT_FILE.getParent());
            Files.writeString(FORMAT_FILE, format,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException error) {
            System.err.println("[DashAuction] Could not save payment format: " + error);
        }
        tell("Auction LIVE. Every positive payment adds to its sender's total until the timer ends.");
    }

    private static void end() {
        if (endedAnnounced) return;
        endedAnnounced = true;
        winnerDisplayUntil = System.nanoTime() + 20_000_000_000L; // Winner on screen for 20s
        AuctionEngine.Leader winner = AUCTION.leader();
        tell(winner == null ? "Auction ended without any recorded payments."
            : "WINNER: " + winner.name() + " with " + Money.format(winner.total()) + " paid!");
    }

    private static void tell(String text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.getChat().addClientSystemMessage(
            Component.literal("[DashAuction] " + text)));
    }
}
