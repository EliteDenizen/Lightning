package dev.elitedenizen.lightning.util.diagnostics;

import dev.elitedenizen.lightning.Lightning;

import java.util.logging.Logger;

public final class PluginLogger {

    public enum Level {
        WARN(0), INFO(1), DEBUG(2);

        private final int rank;

        Level(int rank) {
            this.rank = rank;
        }

        public boolean allows(Level other) {
            return this.rank >= other.rank;
        }

        public static Level parse(String name) {
            if (name == null) return WARN;
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return WARN;
            }
        }
    }

    private static Logger logger;
    private static volatile Level currentLevel = Level.WARN;

    private PluginLogger() {}

    public static void init(Lightning plugin) {
        logger = plugin.getLogger();
        currentLevel = readLevel(plugin);
    }

    public static void reload(Lightning plugin) {
        Level next = readLevel(plugin);
        if (next != currentLevel) {
            currentLevel = next;
            info("Debug level set to {0}", currentLevel.name());
        }
    }

    private static Level readLevel(Lightning plugin) {
        try {
            return Level.parse(plugin.getConfig().getString("debug", "WARN"));
        } catch (Throwable ignored) {}
        return Level.WARN;
    }

    public static Level getLevel() { return currentLevel; }
    public static boolean isDebug() { return currentLevel.allows(Level.DEBUG); }

    public static void debug(String msg, Object... args) {
        if (!currentLevel.allows(Level.DEBUG) || logger == null) return;
        logger.info("[Debug] " + fmt(msg, args));
    }

    public static void info(String msg, Object... args) {
        if (!currentLevel.allows(Level.INFO) || logger == null) return;
        logger.info(fmt(msg, args));
    }

    public static void warn(String msg, Object... args) {
        if (logger == null) return;
        logger.warning(fmt(msg, args));
    }

    public static void error(String msg, Object... args) {
        if (logger == null) return;
        logger.severe(fmt(msg, args));
    }

    public static void error(String msg, Throwable t, Object... args) {
        if (logger == null) return;
        logger.severe(fmt(msg, args));
        if (t != null) {
            logger.severe(t.getClass().getSimpleName() + ": " + t.getMessage());
            if (currentLevel.allows(Level.DEBUG)) t.printStackTrace();
        }
    }

    public static long startTimer() { return System.nanoTime(); }

    public static void logTime(long startNano, String label) {
        if (!currentLevel.allows(Level.DEBUG)) return;
        debug("{0} took {1}ms", label, (System.nanoTime() - startNano) / 1_000_000);
    }

    private static String fmt(String msg, Object... args) {
        if (msg == null) return "";
        if (args == null || args.length == 0) return msg;
        String result = msg;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] == null ? "null" : String.valueOf(args[i]));
        }
        return result;
    }
}
