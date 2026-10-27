package com.denizenmc.lightning;

import com.denizenmc.lightning.charge.Surface;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Settings {

    private final Lightning plugin;
    private final Set<Surface> surfaces = EnumSet.noneOf(Surface.class);
    private final Map<String, String> messages = new HashMap<>();
    private boolean markerOnly;
    private boolean placeholderApi;
    private boolean guard;
    private long guardMemory;
    private boolean plainItemNames;
    private String faceFallback;
    private int cacheSize;
    private int centerWidth;
    private boolean faceShadow;
    private long pulse;
    private ZoneId zone;
    private boolean debug;

    Settings(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        markerOnly = config.getString("detect", "smart").equalsIgnoreCase("marker");
        placeholderApi = config.getBoolean("placeholders", true);
        guardMemory = Durations.millis(config.getString("guard"), 600_000L);
        guard = guardMemory > 0;
        plainItemNames = !config.getBoolean("items.italics", false);
        faceFallback = config.getString("faces.fallback", "");
        faceShadow = config.getBoolean("faces.shadow", false);
        cacheSize = Math.max(0, config.getInt("cache", 4096));
        debug = config.getBoolean("debug", false);
        centerWidth = Math.max(1, config.getInt("center", 154));
        pulse = Durations.millis(config.getString("pulse"), 1000L);

        String zoneName = config.getString("timezone", "server");
        try {
            zone = zoneName.equalsIgnoreCase("server") ? ZoneId.systemDefault() : ZoneId.of(zoneName);
        } catch (DateTimeException e) {
            zone = ZoneId.systemDefault();
            plugin.getLogger().warning("Unknown time zone '" + zoneName + "'. Using the system default.");
        }

        surfaces.clear();
        for (Surface surface : Surface.values()) {
            if (config.getBoolean("surfaces." + surface.key(), true)) surfaces.add(surface);
        }

        messages.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) messages.put(key, section.getString(key, ""));
        }
    }

    public boolean markerOnly() {
        return markerOnly;
    }

    public boolean reaches(@NotNull final Surface surface) {
        return surfaces.contains(surface);
    }

    public boolean placeholderApi() {
        return placeholderApi;
    }

    public boolean guard() {
        return guard;
    }

    public long guardMemory() {
        return guardMemory;
    }

    public boolean plainItemNames() {
        return plainItemNames;
    }

    public @NotNull String faceFallback() {
        return faceFallback;
    }

    public int cacheSize() {
        return cacheSize;
    }

    public long pulse() {
        return pulse;
    }

    public boolean faceShadow() {
        return faceShadow;
    }

    public int centerWidth() {
        return centerWidth;
    }

    public @NotNull ZoneId zone() {
        return zone;
    }

    // Toggles debug logging until the next configuration reload.
    public boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    public boolean debug() {
        return debug;
    }

    public @NotNull String message(@NotNull final String key) {
        return messages.getOrDefault(key.toLowerCase(Locale.ROOT), key);
    }
}