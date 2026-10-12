package xyz.dashauction;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Host-side observational all-pay auction tracker.
 * Each player has a running total from SERVER game payment notifications, not ordinary chat.
 * Any positive payment can enter. The top total at the deadline wins.
 * No outgoing payments, item handoffs, or refunds are implemented.
 */
public final class AuctionEngine {
    public enum Phase { IDLE, LIVE, ENDED, CANCELLED }
    public enum Result { NEW_LEADER, COUNTED, LATE, INACTIVE }
    public record Leader(String name, BigInteger total) {}

    private static final class Bidder {
        String name;
        BigInteger total;
        Bidder(String name, BigInteger total) {
            this.name = name;
            this.total = total;
        }
    }
    private final Map<String, Bidder> bidders = new LinkedHashMap<>();
    private Phase phase = Phase.IDLE;
    private Leader leader;
    private String itemId = "";
    private BigInteger worth = BigInteger.ZERO;
    private long deadline;

    public void start(String item, BigInteger value, int seconds, long at) {
        if (phase == Phase.LIVE) throw new IllegalStateException("Auction already active");
        if (item == null || item.isBlank() || value == null || value.signum() <= 0)
            throw new IllegalArgumentException("Select an item and enter a positive item worth");
        if (seconds < 5 || seconds > 86400)
            throw new IllegalArgumentException("Time must be 5 to 86400 seconds");
        deadline = Math.addExact(at, TimeUnit.SECONDS.toNanos(seconds));
        bidders.clear();
        leader = null;
        itemId = item;
        worth = value;
        phase = Phase.LIVE;
    }

    public Result payment(String player, BigInteger amount, long receivedAt) {
        if (phase != Phase.LIVE) return Result.INACTIVE;
        if (receivedAt >= deadline) {
            phase = Phase.ENDED;
            return Result.LATE;
        }
        if (player == null || !player.matches("[A-Za-z0-9_.]{1,20}")
            || amount == null || amount.signum() <= 0) return Result.INACTIVE;

        String key = player.toLowerCase(Locale.ROOT);
        Bidder bidder = bidders.get(key);
        if (bidder == null) {
            bidder = new Bidder(player, amount);
            bidders.put(key, bidder);
        } else {
            bidder.total = bidder.total.add(amount);
        }
        // Ties preserve the previous leader, including its earlier position.
        if (leader == null || bidder.total.compareTo(leader.total()) > 0) {
            leader = new Leader(bidder.name, bidder.total);
            return Result.NEW_LEADER;
        }
        if (leader.name().equalsIgnoreCase(player))
            leader = new Leader(bidder.name, bidder.total);
        return Result.COUNTED;
    }

    /** First tick or incoming payment at/after deadline closes the auction. */
    public boolean tick(long now) {
        if (phase == Phase.LIVE && now >= deadline) {
            phase = Phase.ENDED;
            return true;
        }
        return false;
    }
    public void cancel() { if (phase == Phase.LIVE) phase = Phase.CANCELLED; }
    public long secondsLeft(long now) {
        if (phase != Phase.LIVE) return 0;
        long remaining = Math.max(0, deadline - now);
        return remaining / 1_000_000_000L + (remaining % 1_000_000_000L == 0 ? 0 : 1);
    }
    public List<Leader> leaderboard() {
        return bidders.values().stream()
            .map(b -> new Leader(b.name, b.total))
            .sorted((a, b) -> b.total().compareTo(a.total()))
            .toList();
    }
    public Phase phase() { return phase; }
    public Leader leader() { return leader; }
    public String itemId() { return itemId; }
    public BigInteger worth() { return worth; }
}
