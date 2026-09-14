package dev.elitedenizen.lightning.util.text;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.config.ConfigUpdater;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("prefix", "&8[&eLightning&8]");
        DEFAULTS.put("noPermission", "{prefix} &fYou do not have permission to use that.");
        DEFAULTS.put("playerOnly", "{prefix} &fThis command can only be used by players.");
        DEFAULTS.put("consoleOnly", "{prefix} &fThis command can only be used from the console.");
        DEFAULTS.put("reloadSuccess", "{prefix} &fReload complete.");
        DEFAULTS.put("reloadFailed", "{prefix} &fSomething went wrong during the reload, check the console for details.");
        DEFAULTS.put("cooldown", "{prefix} &fPlease wait &e{time} &fbefore using this again.");
        DEFAULTS.put("maxUsesReached", "{prefix} &fYou have reached the usage limit for this command.");
        DEFAULTS.put("wrongWorld", "{prefix} &fThis command cannot be used in this world.");
        DEFAULTS.put("unknownSubcommand", "{prefix} &fUnrecognized subcommand.");
    }

    private final Lightning plugin;
    private YamlConfiguration config;

    public Messages(Lightning plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            for (String code : new String[] { "en_US", "de_DE", "es_ES", "fr_FR", "pt_BR" }) {
                trySave("lang/" + code + ".yml");
            }
        }

        String resourcePath = "lang/" + language + ".yml";
        File file = new File(langFolder, language + ".yml");

        if (plugin.getResource(resourcePath) != null) {
            ConfigUpdater.update(plugin, resourcePath, file);
        } else if (!file.exists()) {
            PluginLogger.warn("No lang/{0}.yml on disk and it isn't a language Lightning ships, "
                    + "falling back to {1}", language, DEFAULT_LANGUAGE);
            file = new File(langFolder, DEFAULT_LANGUAGE + ".yml");
            ConfigUpdater.update(plugin, "lang/" + DEFAULT_LANGUAGE + ".yml", file);
        }

        config = YamlConfiguration.loadConfiguration(file);
        checkMissing(language);
    }

    private void trySave(String path) {
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ignored) {}
    }

    public String get(String key, String... replacements) {
        String msg = config.getString(key);
        if (msg == null || msg.isEmpty()) {
            msg = DEFAULTS.getOrDefault(key, "&c[missing message: " + key + "]");
        }
        String prefix = config.getString("prefix", DEFAULTS.get("prefix"));
        msg = msg.replace("{prefix}", prefix);
        if (replacements != null) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ColorFormatter.colorize(msg);
    }

    public String getPrefix() {
        return get("prefix");
    }

    private void checkMissing(String language) {
        int missing = 0;
        for (String key : DEFAULTS.keySet()) {
            if (!config.contains(key)) {
                missing++;
                PluginLogger.debug("lang/{0}.yml doesn't have '{1}', using the built in English wording for now",
                        language, key);
            }
        }
        if (missing > 0) {
            plugin.getLogger().info(missing + " message(s) missing from your language file, falling back to English for those.");
        }
    }
}
