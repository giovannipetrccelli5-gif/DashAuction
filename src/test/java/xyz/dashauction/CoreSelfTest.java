package xyz.dashauction;
import java.math.BigInteger;

public final class CoreSelfTest {
  private static void ok(boolean b, String s) { if (!b) throw new AssertionError(s); }
  public static void main(String[] a) {
    ok(Money.parse("1.1b").equals(BigInteger.valueOf(1_100_000_000L)), "1.1B");
    ok(Money.parse("1QT").equals(BigInteger.TEN.pow(18)), "QT");
    ok(Money.format(Money.parse("1.1b")).equals("1.1B"), "format");
    for (String s : new String[]{"-1b", "0b", "1.1", "1e9", "1,00b"}) {
      try { Money.parse(s); throw new AssertionError("Allowed "+s); }
      catch (IllegalArgumentException expected) {}
    }
    PaymentTemplate p = new PaymentTemplate("{player} paid you $"+"{amount}");
    ok(p.parse("Dash paid you $1.1b").orElseThrow().amount().equals(Money.parse("1.1b")), "parser");
    ok(p.parse("Dash: Steve paid you $1b").isEmpty(), "spoofed chat");
    AuctionEngine e = new AuctionEngine();
    long at = 80_000_000_000L;
    e.start("minecraft:diamond", Money.parse("2b"), Money.parse("1b"), 60, at);
    ok(e.payment("A", Money.parse("900m"), at+1) == AuctionEngine.Result.BELOW_MINIMUM, "low");
    ok(e.payment("A", Money.parse("1b"), at+2) == AuctionEngine.Result.NEW_LEADER, "1b");
    ok(e.payment("B", Money.parse("1b"), at+3) == AuctionEngine.Result.NOT_HIGHER, "tie");
    ok(e.payment("C", Money.parse("1.1b"), at+4) == AuctionEngine.Result.NEW_LEADER, "1.1b");
    ok(e.phase() == AuctionEngine.Phase.LIVE, "keep listening after leader");
    ok(e.payment("D", Money.parse("9b"), at+60_000_000_000L) == AuctionEngine.Result.LATE, "exact deadline");
    ok(e.leader().name().equals("C"), "late not winner");
    ok(e.phase() == AuctionEngine.Phase.ENDED, "ended");
    ok(e.payment("E", Money.parse("10b"), at+60_000_000_001L) == AuctionEngine.Result.INACTIVE, "locked");
    System.out.println("All core checks passed.");
  }
}
