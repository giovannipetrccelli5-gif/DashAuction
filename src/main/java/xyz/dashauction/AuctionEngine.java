package xyz.dashauction;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/** Highest individual incoming payment wins at the strict monotonic deadline. No refunds or deliveries. */
public final class AuctionEngine {
  public enum Phase { IDLE, LIVE, ENDED, CANCELLED }
  public enum Result { NEW_LEADER, BELOW_MINIMUM, NOT_HIGHER, LATE, INACTIVE }
  public record Leader(String name, BigInteger amount) {}
  private Phase phase = Phase.IDLE;
  private Leader leader;
  private String itemId = "";
  private BigInteger minimum = BigInteger.ZERO, worth = BigInteger.ZERO;
  private long deadline;
  public void start(String item, BigInteger value, BigInteger min, int seconds, long at) {
    if (phase == Phase.LIVE) throw new IllegalStateException("Auction already active");
    if (item == null || item.isBlank() || value == null || min == null || value.signum() <= 0 || min.signum() <= 0)
      throw new IllegalArgumentException("Select item and positive amounts");
    if (seconds < 5 || seconds > 86400) throw new IllegalArgumentException("Time: 5 to 86400 seconds");
    deadline = Math.addExact(at, TimeUnit.SECONDS.toNanos(seconds));
    phase = Phase.LIVE; leader = null; itemId = item; worth = value; minimum = min;
  }
  public Result payment(String name, BigInteger amount, long received) {
    if (phase != Phase.LIVE) return Result.INACTIVE;
    if (received >= deadline) { phase = Phase.ENDED; return Result.LATE; }
    if (name == null || name.isBlank() || amount == null || amount.signum() <= 0) return Result.INACTIVE;
    if (amount.compareTo(minimum) < 0) return Result.BELOW_MINIMUM;
    if (leader != null && amount.compareTo(leader.amount()) <= 0) return Result.NOT_HIGHER;
    leader = new Leader(name, amount);
    return Result.NEW_LEADER;
  }
  public boolean tick(long now) {
    if (phase == Phase.LIVE && now >= deadline) { phase = Phase.ENDED; return true; }
    return false;
  }
  public void cancel() { if (phase == Phase.LIVE) phase = Phase.CANCELLED; }
  public long secondsLeft(long now) {
    if (phase != Phase.LIVE) return 0;
    long ns = Math.max(0, deadline-now);
    return ns / 1_000_000_000L + (ns % 1_000_000_000L == 0 ? 0 : 1);
  }
  public Phase phase() { return phase; }
  public Leader leader() { return leader; }
  public String itemId() { return itemId; }
  public BigInteger worth() { return worth; }
  public BigInteger minimum() { return minimum; }
}
