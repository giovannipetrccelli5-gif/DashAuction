package xyz.dashauction;

import java.math.*;
import java.util.*;
import java.util.regex.*;

/** Exact whole-number money; 1.1B means exactly 1,100,000,000. */
public final class Money {
    private static final Pattern NUMBER = Pattern.compile("^([0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.([0-9]{1,9}))?(QT|Q|T|B|M|K)?$", Pattern.CASE_INSENSITIVE);
    private static final String[] LABELS = {"QT", "Q", "T", "B", "M", "K"};
    private static final int[] POWERS = {18, 15, 12, 9, 6, 3};
    private Money() {}

    public static BigInteger parse(String input) {
        if (input == null || input.length() > 90) throw new IllegalArgumentException("Invalid amount");
        Matcher matcher = NUMBER.matcher(input.strip());
        if (!matcher.matches()) throw new IllegalArgumentException("Use 500m, 1b, 1.1b, etc.");
        String whole = matcher.group(1).replace(",", "");
        if (whole.length() > 60) throw new IllegalArgumentException("Amount too large");
        String fraction = matcher.group(2);
        BigDecimal number = new BigDecimal(whole + (fraction == null ? "" : "." + fraction));
        int power = 0;
        if (matcher.group(3) != null) {
            for (int i = 0; i < LABELS.length; i++)
                if (LABELS[i].equalsIgnoreCase(matcher.group(3))) { power = POWERS[i]; break; }
        }
        try {
            BigInteger result = number.movePointRight(power).toBigIntegerExact();
            if (result.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
            return result;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must be a whole number", e);
        }
    }

    public static String format(BigInteger value) {
        if (value == null) return "-";
        for (int i = 0; i < POWERS.length; i++) {
            BigDecimal unit = BigDecimal.TEN.pow(POWERS[i]);
            if (value.abs().compareTo(unit.toBigInteger()) >= 0)
                return new BigDecimal(value).divide(unit, 2, RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString() + LABELS[i];
        }
        return value.toString();
    }
}
