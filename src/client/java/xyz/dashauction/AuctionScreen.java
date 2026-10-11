package xyz.dashauction;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.stream.Collectors;

/** A searchable, paged grid of every item in the game's built-in item registry. */
public final class AuctionScreen extends Screen {
    private static final int COLUMNS = 7, PAGE_SIZE = 28;
    private final List<Item> allItems = BuiltInRegistries.ITEM.stream()
        .filter(item -> item != Items.AIR)
        .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
        .collect(Collectors.toList());
    private List<Item> filtered = allItems;
    private Item selected = Items.DIAMOND;
    private EditBox search, minimum, worth, seconds, format;
    private Button previous, next, startButton;
    private int page, left, top;
    private String status = "Set the item, minimum payment, time and worth.";

    public AuctionScreen() {
        super(Component.literal("DashAuction"));
    }

    @Override protected void init() {
        left = Math.max(5, (width - 330) / 2);
        top = Math.max(10, (height - 302) / 2);
        int right = left + 163;
        search = field(left, top+29, 147, "Search items", "");
        search.setResponder(this::filterItems);
        minimum = field(right, top+47, 157, "Minimum payment", "1b");
        worth = field(right, top+85, 157, "Item worth", "2b");
        seconds = field(right, top+123, 157, "Seconds", "60");
        format = field(left, top+202, 320, "Payment message format", DashAuctionClient.formatText());
        format.setMaxLength(160);

        for (int i = 0; i < PAGE_SIZE; i++) {
            final int slot = i;
            int x = left + i % COLUMNS * 21;
            int y = top + 55 + i / COLUMNS * 22;
            addRenderableWidget(Button.builder(Component.literal(" "), b -> {
                Item item = itemAt(slot);
                if (item != null) {
                    selected = item;
                    status = "Selected " + BuiltInRegistries.ITEM.getKey(item);
                }
            }).bounds(x, y, 20, 20).build());
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0,page-1))
            .bounds(left, top+149, 24, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(maxPage(),page+1))
            .bounds(left+122, top+149, 24, 20).build());
        startButton = addRenderableWidget(Button.builder(Component.literal("START"), b -> start())
            .bounds(right, top+149, 157, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
            .bounds(left+222, top+226, 98, 20).build());
    }

    private EditBox field(int x, int y, int w, String title, String initial) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.literal(title));
        box.setMaxLength(90);
        box.setValue(initial);
        addWidget(box);
        return box;
    }

    private void filterItems(String term) {
        page = 0;
        String query = term.toLowerCase(Locale.ROOT).strip();
        filtered = allItems.stream().filter(item ->
            BuiltInRegistries.ITEM.getKey(item).toString().contains(query)
            || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query))
            .collect(Collectors.toList());
    }
    private int maxPage() { return Math.max(0, (filtered.size()-1) / PAGE_SIZE); }
    private Item itemAt(int slot) {
        int idx = page * PAGE_SIZE + slot;
        return idx >= 0 && idx < filtered.size() ? filtered.get(idx) : null;
    }

    private void start() {
        try {
            DashAuctionClient.start(BuiltInRegistries.ITEM.getKey(selected).toString(),
                Money.parse(worth.getValue()), Money.parse(minimum.getValue()),
                Integer.parseInt(seconds.getValue()), format.getValue());
            status = "LIVE - keep this client connected. You may close the GUI.";
        } catch (IllegalArgumentException | IllegalStateException e) {
            status = "Error: " + e.getMessage();
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        previous.active = page > 0;
        next.active = page < maxPage();
        startButton.active = DashAuctionClient.AUCTION.phase() != AuctionEngine.Phase.LIVE;
        super.extractRenderState(g,mx,my,delta);
        int white=0xFFFFFFFF, gray=0xFFCCCCCC;
        g.centeredText(font,title,width/2,top+8,white);
        g.text(font,"Items: "+filtered.size(),left,top+17,gray);
        g.text(font,"Minimum payment",left+163,top+35,gray);
        g.text(font,"Item worth (informational)",left+163,top+73,gray);
        g.text(font,"Auction time (seconds)",left+163,top+111,gray);
        g.text(font,"Payment notification format (use {player} and {amount})",left,top+190,gray);
        g.text(font,"Page "+(page+1)+"/"+(maxPage()+1),left+30,top+155,white);
        search.extractRenderState(g,mx,my,delta);
        minimum.extractRenderState(g,mx,my,delta);
        worth.extractRenderState(g,mx,my,delta);
        seconds.extractRenderState(g,mx,my,delta);
        format.extractRenderState(g,mx,my,delta);
        for(int i=0;i<PAGE_SIZE;i++){
            Item item=itemAt(i);
            if(item==null) continue;
            int x=left+i%COLUMNS*21+2,y=top+55+i/COLUMNS*22+2;
            g.item(new ItemStack(item),x,y);
            if(mx>=x && mx<x+17 && my>=y && my<y+17)
                g.setTooltipForNextFrame(Component.literal(BuiltInRegistries.ITEM.getKey(item).toString()),mx,my);
        }
        g.text(font,"Selected: "+BuiltInRegistries.ITEM.getKey(selected),left,top+252,white);
        g.text(font,status,left,top+267,0xFFFFDD88);
        AuctionEngine auction=DashAuctionClient.AUCTION;
        AuctionEngine.Leader leader=auction.leader();
        if(auction.phase()==AuctionEngine.Phase.LIVE){
            g.text(font,"LIVE: "+auction.secondsLeft(System.nanoTime())+"s remaining",left,top+283,0xFF55FF55);
            g.text(font,leader==null?"No qualifying payments yet":
                "Highest: "+leader.name()+" "+Money.format(leader.amount()),left,top+296,white);
        } else if(auction.phase()==AuctionEngine.Phase.ENDED){
            g.text(font,leader==null?"ENDED: no winner":
                "ENDED: "+leader.name()+" "+Money.format(leader.amount()),left,top+283,0xFFFFFF55);
        }
    }
}
