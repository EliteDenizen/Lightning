package com.denizenmc.lightning.charge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum Surface {

    CHAT("chat", true, false),
    ACTIONBAR("actionbar", false, false),
    TITLES("titles", false, false),
    BOSSBARS("bossbars", false, false),
    TABLIST("tablist", false, false),
    SCOREBOARDS("scoreboards", false, false),
    INVENTORIES("inventories", false, false),
    ITEMS("items", false, true),
    ENTITIES("entities", false, true),
    KICKS("kicks", false, false),
    DIALOGS("dialogs", false, false),
    MOTD("motd", false, false);

    private final String key;
    private final boolean bolts;
    private final boolean safe;

    Surface(final String key, final boolean bolts, final boolean safe) {
        this.key = key;
        this.bolts = bolts;
        this.safe = safe;
    }

    public static @Nullable Surface byKey(@NotNull final String key) {
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (Surface surface : values()) {
            if (surface.key.equals(wanted)) return surface;
        }
        return null;
    }

    public @NotNull String key() {
        return key;
    }

    // Bolts only trigger in chat. Repeated updates to tab lists and scoreboards could otherwise
    // cause effects such as sounds to play repeatedly.
    public boolean bolts() {
        return bolts;
    }

    // Items and entities may contain player-provided text, so click and hover tags are disabled for them.
    public boolean safe() {
        return safe;
    }
}