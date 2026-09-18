package dev.jalikdev.lowCore.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {

    public static final long MAX_DURATION_MILLIS = 365L * 24L * 60L * 60L * 1_000L;
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationUtil() {
    }

    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration is empty");
        }

        Matcher matcher = PART.matcher(input.trim());
        int position = 0;
        long total = 0L;
        while (matcher.find()) {
            if (matcher.start() != position) {
                throw new IllegalArgumentException("Invalid duration");
            }

            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
                long unitMillis = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "s" -> 1_000L;
                    case "m" -> 60_000L;
                    case "h" -> 3_600_000L;
                    case "d" -> 86_400_000L;
                    case "w" -> 604_800_000L;
                    default -> throw new IllegalArgumentException("Invalid duration unit");
                };
                total = Math.addExact(total, Math.multiplyExact(amount, unitMillis));
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("Duration is too large", exception);
            }
            position = matcher.end();
        }

        if (position != input.trim().length() || total <= 0L || total > MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("Invalid duration");
        }
        return total;
    }

    public static String formatMillis(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1_000L);
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder result = new StringBuilder();
        append(result, days, "d");
        append(result, hours, "h");
        append(result, minutes, "m");
        append(result, seconds, "s");
        return result.toString();
    }

    private static void append(StringBuilder target, long amount, String unit) {
        if (amount == 0L && !target.isEmpty()) {
            return;
        }
        if (amount > 0L) {
            if (!target.isEmpty()) {
                target.append(' ');
            }
            target.append(amount).append(unit);
        }
    }
}
