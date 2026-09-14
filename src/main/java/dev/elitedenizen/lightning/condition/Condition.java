package dev.elitedenizen.lightning.condition;

import dev.elitedenizen.lightning.action.ActionContext;

import java.util.*;

public final class Condition {

    private static final Condition NONE = new Condition(Collections.<ConditionEntry>emptyList(), null);

    private final List<ConditionEntry> entries;
    private final String failMessage;

    Condition(List<ConditionEntry> entries, String failMessage) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.failMessage = failMessage;
    }

    public boolean passes(ActionContext context) {
        if (entries.isEmpty()) return true;
        for (ConditionEntry entry : entries) {
            if (!entry.test(context)) return false;
        }
        return true;
    }

    public boolean isEmpty() { return entries.isEmpty(); }
    public String getFailMessage() { return failMessage; }

    public static Condition none() { return NONE; }

    public static Condition of(List<ConditionEntry> entries) {
        return of(entries, null);
    }

    public static Condition of(List<ConditionEntry> entries, String failMessage) {
        if (entries == null || entries.isEmpty()) return NONE;
        return new Condition(entries, failMessage);
    }

    public interface ConditionEntry {
        boolean test(ActionContext context);
    }

    public static final class PermissionEntry implements ConditionEntry {
        private final String permission;
        private final boolean negate;

        public PermissionEntry(String raw) {
            if (raw != null && raw.startsWith("!")) {
                this.negate = true;
                this.permission = raw.substring(1).trim();
            } else {
                this.negate = false;
                this.permission = raw != null ? raw.trim() : "";
            }
        }

        @Override
        public boolean test(ActionContext context) {
            boolean has = context.hasPermission(permission);
            return negate != has;
        }
    }

    public static final class ArgCountEntry implements ConditionEntry {
        private final int expected;
        private final String comparator;

        public ArgCountEntry(String raw) {
            String s = raw.trim();
            if (s.startsWith(">=")) { comparator = ">="; expected = parseNum(s.substring(2)); }
            else if (s.startsWith("<=")) { comparator = "<="; expected = parseNum(s.substring(2)); }
            else if (s.startsWith(">")) { comparator = ">";  expected = parseNum(s.substring(1)); }
            else if (s.startsWith("<")) { comparator = "<";  expected = parseNum(s.substring(1)); }
            else { comparator = "=";  expected = parseNum(s); }
        }

        private static int parseNum(String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
        }

        @Override
        public boolean test(ActionContext context) {
            int count = context.getArgCount();
            switch (comparator) {
                case ">=": return count >= expected;
                case "<=": return count <= expected;
                case ">":  return count > expected;
                case "<":  return count < expected;
                default:   return count == expected;
            }
        }
    }

    public static final class WorldEntry implements ConditionEntry {
        private final Set<String> worlds;
        private final boolean negate;

        public WorldEntry(List<String> worlds, boolean negate) {
            Set<String> ws = new HashSet<>();
            for (String w : worlds) {
                if (w != null) ws.add(w.toLowerCase());
            }
            this.worlds = ws;
            this.negate = negate;
        }

        @Override
        public boolean test(ActionContext context) {
            if (context.getPlayer() == null) return !negate;
            boolean in = worlds.contains(context.getPlayer().getWorld().getName().toLowerCase());
            return negate != in;
        }
    }

    public static final class PlaceholderEntry implements ConditionEntry {
        private final String placeholder;
        private final String expected;
        private final boolean ignoreCase;

        public PlaceholderEntry(String placeholder, String expected, boolean ignoreCase) {
            this.placeholder = placeholder;
            this.expected = expected;
            this.ignoreCase = ignoreCase;
        }

        @Override
        public boolean test(ActionContext context) {
            String resolved = context.applyPlaceholders(placeholder);
            return ignoreCase ? resolved.equalsIgnoreCase(expected) : resolved.equals(expected);
        }
    }

    public static final class EveryUsesEntry implements ConditionEntry {
        private final int every;

        public EveryUsesEntry(int every) {
            this.every = Math.max(1, every);
        }

        @Override
        public boolean test(ActionContext context) {
            String raw = context.getVariables().get("uses");
            if (raw == null) return false;
            try {
                int count = Integer.parseInt(raw);
                return count > 0 && count % every == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static final class GameModeEntry implements ConditionEntry {
        private final Set<String> modes;
        private final boolean negate;

        public GameModeEntry(List<String> modes, boolean negate) {
            Set<String> parsed = new HashSet<>();
            for (String m : modes) {
                if (m != null) parsed.add(m.trim().toUpperCase());
            }
            this.modes = parsed;
            this.negate = negate;
        }

        @Override
        public boolean test(ActionContext context) {
            if (context.getPlayer() == null) return !negate;
            String current = context.getPlayer().getGameMode().name();
            boolean in = modes.contains(current);
            return negate != in;
        }
    }

    public static final class HealthEntry implements ConditionEntry {
        private final double min;
        private final double max;

        public HealthEntry(double min, double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean test(ActionContext context) {
            if (context.getPlayer() == null) return false;
            double health = context.getPlayer().getHealth();
            return health >= min && health <= max;
        }
    }
}
