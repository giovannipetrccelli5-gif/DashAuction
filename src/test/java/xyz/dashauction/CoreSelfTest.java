package xyz.dashauction;
import java.math.BigInteger;

public final class CoreSelfTest {
    private static void ok(boolean valid, String label) { if (!valid) throw new AssertionError(label); }
    public static void main(String[] args) {
        ok(Money.parse("1.1b").equals(BigInteger.valueOf(1_100_000_000L)), "1.1b");
        ok(Money.parse("1QT").equals(BigInteger.TEN.pow(18)), "qt");
        ok(Money.format(Money.parse("1.1b")).equals("1.1B"), "format");
        for (String invalid : new String[] {"-1b", "0", "1.1", "1e9", "1,00b"}) {
            try { Money.parse(invalid); throw new AssertionError("Invalid money accepted: " + invalid); }
            catch (IllegalArgumentException expected) {}
        }
        PaymentTemplate template = new PaymentTemplate("{player} paid you $" + "{amount}");
        ok(template.parse("Dash paid you $1.1b").orElseThrow().amount()
            .equals(Money.parse("1.1b")), "parser");
        ok(template.parse("Steve: Dash paid you $1b").isEmpty(), "disallow spoof prefix");

        long t = 42_000_000_000L;
        AuctionEngine a = new AuctionEngine();
        a.start("minecraft:diamond", Money.parse("2b"), 60, t);
        ok(a.payment("Alex", BigInteger.ONE, t+1) == AuctionEngine.Result.NEW_LEADER,
            "any positive amount qualifies");
        ok(a.payment("Steve", Money.parse("1b"), t+2) == AuctionEngine.Result.NEW_LEADER, "new leader");
        ok(a.payment("Alex", Money.parse("500m"), t+3) == AuctionEngine.Result.COUNTED,
            "still less despite adding");
        ok(a.payment("aLeX", Money.parse("600m"), t+4) == AuctionEngine.Result.NEW_LEADER,
            "player's cumulative payments overtake");
        ok(a.leader().name().equals("Alex"), "preserve first player spelling");
        ok(a.leader().total().equals(Money.parse("1.1b").add(BigInteger.ONE)), "cumulative total");
        ok(a.phase() == AuctionEngine.Phase.LIVE, "does not end when leader changes");
        ok(a.payment("Steve", Money.parse("100m").add(BigInteger.ONE), t+5)
            == AuctionEngine.Result.COUNTED, "tie doesn't displace earlier leader");
        ok(a.leader().name().equals("Alex"), "tie preserves leader");
        ok(a.payment("Steve", BigInteger.ONE, t+6) == AuctionEngine.Result.NEW_LEADER,
            "leader changes when total overtakes by 1");
        ok(a.leaderboard().size() == 2, "two bidders in board");
        ok(a.payment("Late", Money.parse("100qt"), t+60_000_000_000L)
            == AuctionEngine.Result.LATE, "exact deadline excluded");
        ok(a.leader().name().equals("Steve"), "late payment cannot win");
        ok(a.phase() == AuctionEngine.Phase.ENDED, "deadline locks");
        ok(a.payment("Late", Money.parse("100qt"), t+60_000_000_001L)
            == AuctionEngine.Result.INACTIVE, "cannot reopen");
        ok(a.leaderboard().stream().noneMatch(e -> e.name().equals("Late")), "late not on leaderboard");
        a.start("minecraft:stick", BigInteger.ONE, 5, t+61_000_000_000L);
        ok(a.leaderboard().isEmpty() && a.leader() == null, "new auction resets");
        ok(a.tick(t+66_000_000_000L), "tick ends without bids");
        ok(!a.tick(t+67_000_000_000L), "end once");
        System.out.println("DashAuction core self-tests passed.");
    }
}
