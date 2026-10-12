package xyz.dashauction;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The auction HUD is absent while idle/cancelled.
 * It is visible throughout LIVE auctions; after ending it shows the winner for 20 seconds.
 */
public final class AuctionHud {
    private AuctionHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("dashauction", "auction_status"),
            (graphics, delta) -> draw(graphics));
    }

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        AuctionEngine auction = DashAuctionClient.AUCTION;
        boolean live = auction.phase() == AuctionEngine.Phase.LIVE;
        boolean finished = DashAuctionClient.showResult();
        if (!live && !finished) return;

        int x = 8, y = 8, white = 0xFFFFFFFF, gray = 0xFFDDDDE6;
        g.fill(x - 4, y - 4, x + 236, y + (live ? 105 : 65), 0xC4151924);
        g.text(client.font, live ? "DASHAUCTION - LIVE" : "DASHAUCTION - FINISHED", x, y, 0xFF55FFFF);
        g.text(client.font, "Item: " + auction.itemId(), x, y + 14, white);
        g.text(client.font, "Worth: " + Money.format(auction.worth()), x, y + 27, gray);
        AuctionEngine.Leader leader = auction.leader();
        if (live) {
            g.text(client.font, "Time left: " + auction.secondsLeft(System.nanoTime()) + "s",
                x, y + 40, 0xFFFFFF55);
            g.text(client.font, leader == null ? "Most paid: No payments yet" :
                "Most paid: " + leader.name() + " - " + Money.format(leader.total()), x, y + 53, white);
            var table = auction.leaderboard();
            if (table.size() >= 2)
                g.text(client.font, "2. " + table.get(1).name() + " - " + Money.format(table.get(1).total()), x, y + 68, gray);
            if (table.size() >= 3)
                g.text(client.font, "3. " + table.get(2).name() + " - " + Money.format(table.get(2).total()), x, y + 81, gray);
            g.text(client.font, "Payments still count until 0s!", x, y + 95, 0xFF99FF99);
        } else {
            g.text(client.font, leader == null ? "No winner" : "WINNER: " + leader.name(), x, y + 40, 0xFFFFFF55);
            if (leader != null)
                g.text(client.font, "Paid: " + Money.format(leader.total()), x, y + 53, white);
        }
    }
}
