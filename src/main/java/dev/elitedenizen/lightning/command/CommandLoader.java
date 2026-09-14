package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionLoader;
import dev.elitedenizen.lightning.action.ActionSet;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.condition.ConditionLoader;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CommandLoader {

    private CommandLoader() {}

    public static List<CustomCommand> loadFromFolder(Lightning plugin) {
        File folder = new File(plugin.getDataFolder(), "commands");
        folder.mkdirs();
        if (plugin.isFirstInstall()) {
            trySave(plugin, "commands/example.yml");
        }

        List<File> files = new ArrayList<>();
        collectYamlFiles(folder, files);

        if (files.isEmpty()) {
            PluginLogger.debug("No yml files found anywhere under commands/, no custom commands loaded");
            return Collections.emptyList();
        }

        List<CustomCommand> all = new ArrayList<>();
        for (File file : files) {
            long start = PluginLogger.startTimer();
            List<CustomCommand> loaded = loadFile(file);
            all.addAll(loaded);
            PluginLogger.logTime(start, "Loading " + file.getName());
            PluginLogger.debug("Loaded {0} command(s) from {1}", loaded.size(), file.getName());
        }

        PluginLogger.info("Picked up {0} custom command(s) spread across {1} file(s) in commands/",
                all.size(), files.size());
        return all;
    }

    private static void collectYamlFiles(File folder, List<File> out) {
        File[] entries = folder.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectYamlFiles(entry, out);
            } else if (entry.getName().endsWith(".yml")) {
                out.add(entry);
            }
        }
    }

    private static void trySave(Lightning plugin, String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (target.exists()) return;
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ignored) {}
    }

    private static List<CustomCommand> loadFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<CustomCommand> result = new ArrayList<>();

        ConfigurationSection section = cfg.getConfigurationSection("commands");
        if (section == null) {
            PluginLogger.warn("{0} has no 'commands:' root key, nothing was loaded from it. "
                    + "Every command file needs a top level 'commands:' section.", file.getName());
            return result;
        }

        ConfigurationSection defaults = cfg.getConfigurationSection("defaults");

        for (String key : section.getKeys(false)) {
            ConfigurationSection cmdSec = section.getConfigurationSection(key);
            if (cmdSec == null) continue;
            CustomCommand cmd = parseCommand(key, cmdSec, defaults, file.getName());
            if (cmd != null) result.add(cmd);
        }

        return result;
    }

    private static CustomCommand parseCommand(String key, ConfigurationSection sec,
            ConfigurationSection defaults, String fileName) {
        String name = sec.getString("name", key).toLowerCase().trim();
        if (name.isEmpty()) {
            PluginLogger.warn("A command entry in {0} has no usable name, skipping it", fileName);
            return null;
        }

        if (!getBoolean(sec, defaults, "enabled", true)) {
            PluginLogger.debug("/{0} is disabled in {1}, skipping", name, fileName);
            return null;
        }

        List<Action> rawActions = ActionLoader.loadActions(sec);
        if (rawActions.isEmpty()) {
            PluginLogger.warn("/{0} in {1} has no actions defined, it will do nothing when run", name, fileName);
        }

        Condition condition = readTopLevelCondition(sec);

        ActionSet actions = ActionSet.builder()
                .addAll(rawActions)
                .condition(condition)
                .groupDelay(sec.get("actionDelay"))
                .build();

        List<String> aliases = readAliases(sec);
        List<String> worlds = sec.getStringList("worlds");

        ConfigurationSection cooldownSec = sec.getConfigurationSection("cooldown");
        ConfigurationSection cooldownDefaults = defaults != null ? defaults.getConfigurationSection("cooldown") : null;
        ConfigurationSection usesSec = sec.getConfigurationSection("uses");
        ConfigurationSection usesDefaults = defaults != null ? defaults.getConfigurationSection("uses") : null;

        CustomCommand.Builder builder = CustomCommand.builder(name)
                .aliases(aliases)
                .permission(getString(sec, defaults, "permission", ""))
                .permissionMessage(getString(sec, defaults, "permissionMessage", ""))
                .permissionDefault(getString(sec, defaults, "permissionDefault", "false"))
                .usage(sec.getString("usage", "/" + name))
                .description(sec.getString("description", ""))
                .playerOnly(getBoolean(sec, defaults, "playerOnly", false))
                .consoleOnly(getBoolean(sec, defaults, "consoleOnly", false))
                .tabComplete(getBoolean(sec, defaults, "tabComplete", true))
                .visible(getBoolean(sec, defaults, "register", true))
                .worlds(worlds)
                .worldDeniedMessage(getString(sec, defaults, "worldDeniedMessage", ""))
                .logUsage(getBoolean(sec, defaults, "logUsage", false))
                .override(getBoolean(sec, defaults, "override", false))
                .sourceFile(fileName)
                .arguments(readArguments(sec))
                .actions(actions);

        applyCooldown(builder, cooldownSec, cooldownDefaults, name, fileName);
        applyUses(builder, usesSec, usesDefaults, name, fileName);

        PluginLogger.debug("Parsed /{0} from {1} (aliases: {2}, actions: {3})",
                name, fileName, aliases, rawActions.size());

        return builder.build();
    }

    private static void applyCooldown(CustomCommand.Builder builder, ConfigurationSection cooldownSec,
            ConfigurationSection cooldownDefaults, String name, String fileName) {
        if (cooldownSec == null && cooldownDefaults == null) return;

        Object durationRaw = cooldownSec != null && cooldownSec.contains("duration")
                ? cooldownSec.get("duration")
                : (cooldownDefaults != null ? cooldownDefaults.get("duration") : null);

        long ms = TickConverter.toMillis(durationRaw);
        String message = getString(cooldownSec, cooldownDefaults, "message", "");
        String bypass = getString(cooldownSec, cooldownDefaults, "bypassPermission", "");
        String group = getString(cooldownSec, cooldownDefaults, "group", "");

        builder.cooldownMs(ms).cooldownMessage(message).cooldownBypass(bypass).cooldownGroup(group);

        if (ms > 0) PluginLogger.debug("/{0} cooldown set to {1}ms", name, ms);
    }

    private static void applyUses(CustomCommand.Builder builder, ConfigurationSection usesSec,
            ConfigurationSection usesDefaults, String name, String fileName) {
        if (usesSec == null && usesDefaults == null) return;

        int limit = usesSec != null && usesSec.contains("limit")
                ? usesSec.getInt("limit")
                : (usesDefaults != null ? usesDefaults.getInt("limit", -1) : -1);

        String scopeRaw = getString(usesSec, usesDefaults, "per", "player");
        CustomCommand.UsesScope scope;
        try {
            scope = CustomCommand.UsesScope.valueOf(scopeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            PluginLogger.warn("/{0} in {1} has an invalid uses 'per' value '{2}', "
                    + "expected 'player' or 'global'. Defaulting to 'player'.", name, fileName, scopeRaw);
            scope = CustomCommand.UsesScope.PLAYER;
        }

        Object resetRaw = usesSec != null && usesSec.contains("resetAfter")
                ? usesSec.get("resetAfter")
                : (usesDefaults != null ? usesDefaults.get("resetAfter") : null);
        long resetMs = resetRaw != null ? TickConverter.toMillis(resetRaw) : 0;

        builder.usesLimit(limit)
                .usesScope(scope)
                .usesBypass(getString(usesSec, usesDefaults, "bypassPermission", ""))
                .usesMessage(getString(usesSec, usesDefaults, "message", ""))
                .usesResetMs(resetMs);

        if (limit > 0) PluginLogger.debug("/{0} uses limit set to {1} (scope: {2})", name, limit, scope);
    }

    private static String getString(ConfigurationSection sec, ConfigurationSection defaults, String key, String fallback) {
        if (sec != null && sec.contains(key)) return sec.getString(key, fallback);
        if (defaults != null && defaults.contains(key)) return defaults.getString(key, fallback);
        return fallback;
    }

    private static boolean getBoolean(ConfigurationSection sec, ConfigurationSection defaults, String key, boolean fallback) {
        if (sec != null && sec.contains(key)) return sec.getBoolean(key, fallback);
        if (defaults != null && defaults.contains(key)) return defaults.getBoolean(key, fallback);
        return fallback;
    }

    private static List<CommandArgument> readArguments(ConfigurationSection sec) {
        List<?> raw = sec.getList("arguments");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<CommandArgument> parsed = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) entry;

            Object name = map.get("name");
            Object required = map.get("required");
            Object suggestions = map.get("suggestions");
            Object pattern = map.get("pattern");

            CommandArgument.Builder builder = CommandArgument.builder(name != null ? name.toString() : "arg")
                    .required(required != null && Boolean.parseBoolean(required.toString()));

            readSuggestions(builder, suggestions);
            readPattern(builder, pattern);

            parsed.add(builder.build());
        }
        return parsed;
    }

    private static void readSuggestions(CommandArgument.Builder builder, Object suggestions) {
        if (suggestions instanceof List) {
            List<String> staticList = new ArrayList<>();
            for (Object s : (List<?>) suggestions) {
                if (s != null) staticList.add(s.toString());
            }
            builder.staticSuggestions(staticList);
            return;
        }

        if (!(suggestions instanceof Map)) return;
        Map<?, ?> map = (Map<?, ?>) suggestions;

        Object values = map.get("values");
        if (values instanceof List) {
            List<String> staticList = new ArrayList<>();
            for (Object s : (List<?>) values) {
                if (s != null) staticList.add(s.toString());
            }
            builder.staticSuggestions(staticList);
            return;
        }

        Object type = map.get("type");
        if (type != null) builder.suggestionKeyword(type.toString());
    }

    private static void readPattern(CommandArgument.Builder builder, Object pattern) {
        if (pattern instanceof String) {
            builder.pattern(pattern.toString(), null);
            return;
        }

        if (!(pattern instanceof Map)) return;
        Map<?, ?> map = (Map<?, ?>) pattern;

        Object regex = map.get("regex");
        Object message = map.get("message");
        if (regex != null) {
            builder.pattern(regex.toString(), message != null ? message.toString() : null);
        }
    }

    private static List<String> readAliases(ConfigurationSection sec) {
        List<String> aliases = sec.getStringList("aliases");
        if (!aliases.isEmpty()) return lowercase(aliases);

        String single = sec.getString("aliases");
        if (single == null) return aliases;

        List<String> parsed = new ArrayList<>();
        for (String a : single.split(",")) {
            String trimmed = a.trim().toLowerCase();
            if (!trimmed.isEmpty()) parsed.add(trimmed);
        }
        return parsed;
    }

    private static List<String> lowercase(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String s : input) out.add(s.toLowerCase());
        return out;
    }

    private static Condition readTopLevelCondition(ConfigurationSection sec) {
        ConfigurationSection condSec = sec.getConfigurationSection("conditions");
        if (condSec == null) condSec = sec.getConfigurationSection("condition");
        return condSec != null ? ConditionLoader.load(condSec) : Condition.none();
    }
}
