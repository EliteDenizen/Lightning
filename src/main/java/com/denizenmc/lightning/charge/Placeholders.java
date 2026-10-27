package com.denizenmc.lightning.charge;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Centralizes PlaceholderAPI calls so sparks and charged text use the same resolution logic.
public final class Placeholders {

    private static volatile boolean enabled;

    private Placeholders() {
    }

    static void enable(final boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static @NotNull String apply(@Nullable final Player viewer, @NotNull final String text) {
        if (!enabled || text.indexOf('%') < 0) return text;
        return Sense.sectionToTags(PlaceholderAPI.setPlaceholders((OfflinePlayer) viewer, text));
    }
}