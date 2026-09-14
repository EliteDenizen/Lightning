package dev.elitedenizen.lightning.settings;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.config.ConfigUpdater;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PluginConfig {

    private final Lightning plugin;
    private final Set<String> ignoredCommands = new HashSet<>();
    private final Set<String> ignoredPlugins = new HashSet<>();
    private boolean runOnCancelled = false;
    private boolean configHelperEnabled = true;
    private String configHelperPrefix = "$";

    public PluginConfig(Lightning plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigUpdater.update(plugin, "config.yml", new File(plugin.getDataFolder(), "config.yml"));
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        ignoredCommands.clear();
        ignoredPlugins.clear();

        for (String cmd : cfg.getStringList("ignore.commands")) {
            ignoredCommands.add(cmd.toLowerCase());
        }
        for (String pl : cfg.getStringList("ignore.plugins")) {
            ignoredPlugins.add(pl.toLowerCase());
        }

        runOnCancelled = cfg.getBoolean("runOnCancelled", false);
        configHelperEnabled = cfg.getBoolean("configHelper.enabled", true);
        configHelperPrefix = cfg.getString("configHelper.prefix", "$");

        PluginLogger.debug("Plugin settings reloaded, {0} ignored command(s) and {1} ignored plugin(s)",
                ignoredCommands.size(), ignoredPlugins.size());
    }

    public Set<String> getIgnoredCommands() { return Collections.unmodifiableSet(ignoredCommands); }
    public Set<String> getIgnoredPlugins() { return Collections.unmodifiableSet(ignoredPlugins); }
    public boolean isRunOnCancelled() { return runOnCancelled; }
    public boolean isConfigHelperEnabled() { return configHelperEnabled; }
    public String getConfigHelperPrefix() { return configHelperPrefix; }

    public boolean isCommandIgnored(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase();
        return ignoredCommands.contains(lower) || ignoredCommands.contains("/" + lower);
    }

    public boolean isPluginIgnored(String name) {
        if (name == null) return false;
        return ignoredPlugins.contains(name.toLowerCase());
    }
}
