package dev.elitedenizen.lightning.rule;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionLoader;
import dev.elitedenizen.lightning.action.ActionSet;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.condition.ConditionLoader;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HookRuleLoader {

    private static final String DEFAULT_KEY = "default";

    private HookRuleLoader() {}

    public static List<Rule> loadFromFolder(Lightning plugin) {
        File folder = new File(plugin.getDataFolder(), "actions");
        folder.mkdirs();
        if (plugin.isFirstInstall()) {
            trySave(plugin, "actions/example.yml");
        }

        List<File> files = new ArrayList<>();
        collectYamlFiles(folder, files);

        if (files.isEmpty()) {
            PluginLogger.debug("No yml files found anywhere under actions/, no plugin hooks loaded");
            return Collections.emptyList();
        }

        List<Rule> rules = new ArrayList<>();
        for (File file : files) {
            rules.addAll(loadFile(file));
        }

        PluginLogger.info("Picked up {0} plugin hook rule(s) spread across {1} file(s) in actions/",
                rules.size(), files.size());
        return rules;
    }

    private static void trySave(Lightning plugin, String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (target.exists()) return;
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ignored) {}
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

    private static List<Rule> loadFile(File file) {
        List<Rule> rules = new ArrayList<>();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String pluginKey : cfg.getKeys(false)) {
            ConfigurationSection pluginSec = cfg.getConfigurationSection(pluginKey);
            if (pluginSec == null) continue;

            ConfigurationSection commandsSec = pluginSec.contains("commands")
                    ? pluginSec.getConfigurationSection("commands")
                    : pluginSec;

            if (commandsSec == null) continue;

            Set<String> declaredLabels = new HashSet<>();

            for (String cmdKey : commandsSec.getKeys(false)) {
                if (cmdKey.equalsIgnoreCase(DEFAULT_KEY)) continue;

                ConfigurationSection cmdSec = commandsSec.getConfigurationSection(cmdKey);
                if (cmdSec == null) continue;

                List<String> patterns = expandPatterns(cmdKey, cmdSec);
                declaredLabels.addAll(patterns);

                rules.addAll(buildRules(pluginKey, cmdSec, patterns, file.getName()));
            }

            ConfigurationSection defaultSec = commandsSec.getConfigurationSection(DEFAULT_KEY);
            if (defaultSec != null) {
                Rule defaultRule = buildDefaultRule(pluginKey, defaultSec, declaredLabels, file.getName());
                if (defaultRule != null) rules.add(defaultRule);
            }
        }

        return rules;
    }

    private static List<Rule> buildRules(String pluginKey, ConfigurationSection cmdSec, List<String> patterns, String fileName) {
        List<Action> actions = ActionLoader.loadActions(cmdSec);
        if (actions.isEmpty() || patterns.isEmpty()) return Collections.emptyList();

        ActionSet actionSet = ActionSet.builder()
                .addAll(actions)
                .condition(readCondition(cmdSec))
                .build();

        List<Rule> built = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            Rule rule = Rule.builder()
                    .plugin(pluginKey)
                    .pattern(pattern)
                    .actions(actionSet)
                    .build();
            built.add(rule);
        }

        PluginLogger.debug("Loaded hook rule for {0}/{1} from {2}", pluginKey, patterns, fileName);
        return built;
    }

    private static Rule buildDefaultRule(String pluginKey, ConfigurationSection defaultSec,
            Set<String> declaredLabels, String fileName) {
        List<Action> actions = ActionLoader.loadActions(defaultSec);
        if (actions.isEmpty()) return null;

        Set<String> exclude = new HashSet<>(declaredLabels);
        for (String s : defaultSec.getStringList("blacklist")) {
            exclude.add(s.toLowerCase());
        }

        Set<String> include = new HashSet<>();
        for (String s : defaultSec.getStringList("whitelist")) {
            include.add(s.toLowerCase());
        }

        ActionSet actionSet = ActionSet.builder()
                .addAll(actions)
                .condition(readCondition(defaultSec))
                .build();

        Rule rule = Rule.builder()
                .plugin(pluginKey)
                .actions(actionSet)
                .exclude(exclude)
                .include(include)
                .build();

        PluginLogger.debug("Loaded default rule for {0} from {1} (excluding {2} command(s))",
                pluginKey, fileName, exclude.size());
        return rule;
    }

    private static Condition readCondition(ConfigurationSection sec) {
        ConfigurationSection condSec = sec.getConfigurationSection("conditions");
        if (condSec == null) condSec = sec.getConfigurationSection("condition");
        return condSec != null ? ConditionLoader.load(condSec) : Condition.none();
    }

    private static List<String> expandPatterns(String key, ConfigurationSection section) {
        List<String> result = new ArrayList<>();

        List<String> listed = section.getStringList("commands");
        if (!listed.isEmpty()) {
            result.addAll(listed);
            return result;
        }

        String inlined = section.getString("commands");
        if (inlined != null) {
            for (String s : inlined.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
            return result;
        }

        for (String s : key.split("\\|")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
