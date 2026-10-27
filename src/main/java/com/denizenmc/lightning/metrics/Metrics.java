package com.denizenmc.lightning.metrics;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ArrowStats metrics client for Bukkit, Spigot, and Paper.
 * Relocate this package when shading to avoid conflicts with other plugins
 * that include their own copy. See the README for details.
 *
 * <p>Usage in your plugin's onEnable():</p>
 * <pre>
 * Metrics metrics = new Metrics(this, 1); // 1 = your plugin's ArrowStats ID
 * metrics.addCustomChart(new SimplePie("language", () -> getConfig().getString("lang")));
 * </pre>
 *
 * <p>The shared configuration file at plugins/ArrowStats/config.yml stores the
 * anonymous server UUID and a global opt-out setting for all plugins using
 * ArrowStats on this server. Errors are handled internally and do not propagate
 * to the host plugin.</p>
 */
public class Metrics {

    /**
     * Default ingest URL for the public ArrowStats instance.
     * Use the three-argument constructor to specify another endpoint, such as
     * a local development server or your own ArrowStats deployment.
     */
    private static final String DEFAULT_SUBMIT_URL = "https://arrowstats.org/api/v1/ingest";

    private final JavaPlugin plugin;
    private final MetricsBase metricsBase;

    public Metrics(JavaPlugin plugin, int pluginId) {
        this(plugin, pluginId, DEFAULT_SUBMIT_URL);
    }

    public Metrics(JavaPlugin plugin, int pluginId, String submitUrl) {
        this.plugin = plugin;

        // Shared across all plugins on this server: one UUID and one opt-out setting.
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "ArrowStats");
        File configFile = new File(bStatsFolder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            config.options().header(
                    "ArrowStats collects anonymous usage data for plugins on this server.\n"
                            + "Set 'enabled' to false to opt out for all plugins that use ArrowStats.");
            config.options().copyDefaults(true);
            try {
                config.save(configFile);
            } catch (IOException ignored) {
                // Non-fatal: fall back to the in-memory defaults for this run.
            }
        }

        boolean enabled = config.getBoolean("enabled", true);
        String serverUuid = config.getString("serverUuid", UUID.randomUUID().toString());
        boolean logErrors = config.getBoolean("logFailedRequests", false);
        boolean logSentData = config.getBoolean("logSentData", false);
        boolean logResponse = config.getBoolean("logResponseStatusText", false);

        this.metricsBase = new MetricsBase(
                "bukkit",
                serverUuid,
                pluginId,
                enabled,
                submitUrl == null ? DEFAULT_SUBMIT_URL : submitUrl,
                this::appendPlatformData,
                this::appendServiceData,
                plugin::isEnabled,
                (message, error) -> plugin.getLogger().log(Level.WARNING, message, error),
                message -> plugin.getLogger().log(Level.INFO, message),
                logErrors,
                logSentData,
                logResponse);
    }

    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }

    public void shutdown() {
        metricsBase.shutdown();
    }

    private void appendPlatformData(JsonObjectBuilder builder) {
        builder.appendField("playerCount", getPlayerCount());
        builder.appendField("onlineMode", Bukkit.getOnlineMode());
        builder.appendField("software", Bukkit.getName()); // Paper, Spigot, Purpur, or CraftBukkit.
        builder.appendField("mcVersion", getMinecraftVersion());
        builder.appendField("javaVersion", System.getProperty("java.version"));
        builder.appendField("os", System.getProperty("os.name"));
        builder.appendField("arch", System.getProperty("os.arch"));
        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
    }

    private void appendServiceData(JsonObjectBuilder builder) {
        builder.appendField("pluginVersion", plugin.getDescription().getVersion());
    }

    private int getPlayerCount() {
        try {
            return Bukkit.getOnlinePlayers().size();
        } catch (Throwable t) {
            return 0; // A failed data read must not interrupt metrics submission.
        }
    }

    /** Converts a version such as "1.21.4-R0.1-SNAPSHOT" to "1.21.4". */
    private String getMinecraftVersion() {
        String version = Bukkit.getBukkitVersion();
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }
}
