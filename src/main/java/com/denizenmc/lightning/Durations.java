package com.denizenmc.lightning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class Durations {

    private Durations() {
    }

    // Formats a duration using at most two units, such as "2h 15m" or "45s".
    public static @NotNull String format(final long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long[] sizes = {86_400, 3_600, 60, 1};
        String[] names = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < sizes.length && shown < 2; i++) {
            long amount = seconds / sizes[i];
            seconds %= sizes[i];
            if (amount == 0) {
                if (out.isEmpty()) continue;
                break;
            }
            if (!out.isEmpty()) out.append(' ');
            out.append(amount).append(names[i]);
            shown++;
        }
        return out.isEmpty() ? "0s" : out.toString();
    }

    // Parses a duration with an optional unit. Returns the fallback for invalid input or unsupported units.
    public static long millis(@Nullable final String input, final long fallback) {
        if (input == null || input.isBlank()) return fallback;
        String text = input.trim().toLowerCase(Locale.ROOT);
        int split = 0;
        while (split < text.length() && (Character.isDigit(text.charAt(split)) || text.charAt(split) == '.')) split++;
        if (split == 0) return fallback;

        double amount;
        try {
            amount = Double.parseDouble(text.substring(0, split));
        } catch (NumberFormatException e) {
            return fallback;
        }

        long unit = switch (text.substring(split).trim()) {
            case "ms" -> 1L;
            case "", "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            case "w" -> 604_800_000L;
            case "mo" -> 2_592_000_000L;
            default -> -1L;
        };
        return unit < 0 ? fallback : (long) (amount * unit);
    }
}
