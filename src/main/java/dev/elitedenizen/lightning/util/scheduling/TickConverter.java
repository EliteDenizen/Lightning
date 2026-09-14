package dev.elitedenizen.lightning.util.scheduling;

import java.util.Locale;

public final class TickConverter {

    private TickConverter() {}

    public static long toTicks(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return Math.max(0, ((Number) value).longValue());

        String s = value.toString().trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) return Math.max(1, Long.parseLong(s.substring(0, s.length() - 2)) / 50L);
            if (s.endsWith("h"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 72000L;
            if (s.endsWith("m"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 1200L;
            if (s.endsWith("s"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 20L;
            if (s.endsWith("t"))  return Long.parseLong(s.substring(0, s.length() - 1));
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static long toMillis(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return Math.max(0, ((Number) value).longValue());

        String s = value.toString().trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
            if (s.endsWith("h"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 3600000L;
            if (s.endsWith("m"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 60000L;
            if (s.endsWith("s"))  return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
            return Long.parseLong(s) * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String format(long millis) {
        if (millis <= 0) return "0s";
        long secs = millis / 1000;
        long mins = secs / 60;
        long hrs = mins / 60;
        if (hrs > 0) {
            long remMins = mins % 60;
            return remMins > 0 ? hrs + "h " + remMins + "m" : hrs + "h";
        }
        if (mins > 0) {
            long remSecs = secs % 60;
            return remSecs > 0 ? mins + "m " + remSecs + "s" : mins + "m";
        }
        return secs + "s";
    }

    public static String formatTicks(long ticks) {
        if (ticks <= 0) return "now";
        return format(ticks * 50);
    }
}
