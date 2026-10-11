package xyz.dashauction;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.*;

/**
 * Only used on server-generated game messages, never player chat.
 * Confirm the server's actual payment wording before using it with live bids.
 */
public final class PaymentTemplate {
    public static final String DEFAULT = "{player} paid you $" + "{amount}";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{player\\}|\\{amount\\}");
    private static final String PLAYER = "[A-Za-z0-9_.]{1,20}";
    private static final String AMOUNT = "(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]{1,9})?(?:QT|Q|T|B|M|K)?";
    private final Pattern pattern;
    public record Payment(String player, BigInteger amount) {}

    public PaymentTemplate(String template) {
        if (template == null || template.isBlank() || template.length() > 160)
            throw new IllegalArgumentException("Template must be 1-160 characters");
        Matcher tokens = PLACEHOLDER.matcher(template);
        StringBuilder regex = new StringBuilder("^");
        int end = 0, players = 0, amounts = 0;
        while (tokens.find()) {
            regex.append(Pattern.quote(template.substring(end, tokens.start())));
            if (tokens.group().equals("{player}")) {
                regex.append("(?<player>").append(PLAYER).append(")");
                players++;
            } else {
                regex.append("(?<amount>").append(AMOUNT).append(")");
                amounts++;
            }
            end = tokens.end();
        }
        if (players != 1 || amounts != 1)
            throw new IllegalArgumentException("Include exactly one {player} and one {amount}");
        regex.append(Pattern.quote(template.substring(end))).append("$");
        pattern = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public Optional<Payment> parse(String message) {
        if (message == null || message.length() > 512) return Optional.empty();
        Matcher found = pattern.matcher(message.strip());
        if (!found.matches()) return Optional.empty();
        try {
            return Optional.of(new Payment(found.group("player"), Money.parse(found.group("amount"))));
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
