package dev.elitedenizen.lightning.condition;

import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ConditionLoader {

    private ConditionLoader() {}

    public static Condition load(ConfigurationSection section) {
        if (section == null) return Condition.none();

        List<Condition.ConditionEntry> entries = new ArrayList<>();

        Object permissions = section.get("permission");
        if (permissions instanceof List) {
            for (Object p : (List<?>) permissions) {
                if (p != null && !p.toString().isEmpty()) {
                    entries.add(new Condition.PermissionEntry(p.toString()));
                }
            }
        } else if (permissions instanceof String) {
            String p = permissions.toString().trim();
            if (!p.isEmpty()) entries.add(new Condition.PermissionEntry(p));
        }

        String argCount = section.getString("arguments");
        if (argCount != null && !argCount.trim().isEmpty()) {
            try {
                entries.add(new Condition.ArgCountEntry(argCount));
            } catch (Exception e) {
                PluginLogger.warn("'{0}' isn't a valid arguments condition, try something like '>=2' or '=1'", argCount);
            }
        }

        List<String> worlds = section.getStringList("worlds");
        if (!worlds.isEmpty()) {
            boolean negate = section.getBoolean("worldsNegate", false);
            entries.add(new Condition.WorldEntry(worlds, negate));
        }

        ConfigurationSection placeholders = section.getConfigurationSection("placeholder");
        if (placeholders != null) {
            for (String key : placeholders.getKeys(false)) {
                String expected = placeholders.getString(key + ".equals");
                if (expected == null) expected = placeholders.getString(key);
                if (expected != null) {
                    boolean ignoreCase = placeholders.getBoolean(key + ".ignoreCase", true);
                    entries.add(new Condition.PlaceholderEntry(key, expected, ignoreCase));
                }
            }
        }

        if (section.contains("everyUses")) {
            int every = section.getInt("everyUses", 1);
            if (every > 0) {
                entries.add(new Condition.EveryUsesEntry(every));
            } else {
                PluginLogger.warn("everyUses needs to be at least 1, got {0}", every);
            }
        }

        Object gameMode = section.get("gameMode");
        if (gameMode != null) {
            List<String> modes = new ArrayList<>();
            boolean negate = false;

            if (gameMode instanceof List) {
                for (Object m : (List<?>) gameMode) {
                    if (m != null) modes.add(m.toString());
                }
            } else {
                String raw = gameMode.toString().trim();
                negate = raw.startsWith("!");
                if (negate) raw = raw.substring(1);
                for (String m : raw.split(",")) {
                    if (!m.trim().isEmpty()) modes.add(m.trim());
                }
            }

            if (!modes.isEmpty()) entries.add(new Condition.GameModeEntry(modes, negate));
        }

        ConfigurationSection health = section.getConfigurationSection("health");
        if (health != null) {
            double min = health.getDouble("min", 0);
            double max = health.getDouble("max", Double.MAX_VALUE);
            entries.add(new Condition.HealthEntry(min, max));
        }

        if (entries.isEmpty()) return Condition.none();

        PluginLogger.debug("Loaded {0} condition(s) from section '{1}'",
                entries.size(), section.getName());

        String failMessage = section.getString("message");
        return Condition.of(entries, failMessage);
    }
}
