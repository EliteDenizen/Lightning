package dev.elitedenizen.lightning.util.config;

import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigUpdater {

    private ConfigUpdater() {}

    public static boolean update(Plugin plugin, String resourcePath, File target) {
        if (!target.exists()) {
            target.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
            PluginLogger.info("{0} was missing, restored it from the plugin's defaults", target.getName());
            return true;
        }

        InputStream resource = plugin.getResource(resourcePath);
        if (resource == null) return false;

        YamlConfiguration disk = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8));

        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (!disk.contains(key)) {
                disk.set(key, defaults.get(key));
                added++;
            }
        }

        if (added == 0) return false;

        try {
            disk.save(target);
            PluginLogger.info("Added {0} new option(s) to {1}", added, target.getName());
            return true;
        } catch (IOException e) {
            PluginLogger.error("Could not save the updated {0}", e, target.getName());
            return false;
        }
    }
}
