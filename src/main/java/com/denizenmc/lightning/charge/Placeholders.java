package com.denizenmc.lightning.charge;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Centralizes PlaceholderAPI calls so sparks and charged text use the same resolution logic.
public final class Placeholders {

    /**
     * How much formatting a placeholder value may carry into the message.
     * Values often contain text players control, such as nicknames, team names or chat prefixes,
     * so by default they may only change how text looks.
     */
    public enum Trust {
        // Colors, decorations, gradients and fonts are kept; clicks, hovers, Sparks and Bolts are shown as text.
        FORMATTING,
        // Every tag in a value is processed, as in versions before 0.2.2.
        ALL,
        // Every tag in a value is shown as text. Legacy § colors are still converted.
        NONE;

        public static @NotNull Trust of(@Nullable final String name) {
            if (name == null) return FORMATTING;
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "all", "true" -> ALL;
                case "none", "false" -> NONE;
                default -> FORMATTING;
            };
        }
    }

    // Placeholders look like %identifier% or %identifier_arguments%. Text such as "50% off and 20%" is left alone.
    private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9][A-Za-z0-9-]*(?:_[^%\\n]*)?)%");
    private static final Pattern TAG = Pattern.compile("<([/!]?)([#a-zA-Z0-9_-]+)");
    private static final Set<String> FORMATTING = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "grey",
            "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
            "color", "colour", "c", "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st",
            "obfuscated", "obf", "reset", "gradient", "rainbow", "transition", "shadow", "font", "pride",
            "newline", "br", "n", "smallcaps", "sc", "upper", "lower", "center");

    private static volatile boolean enabled;
    private static volatile Trust trust = Trust.FORMATTING;
    // Set when a value on this thread had tags escaped, so the caller knows the result holds literal tag text
    // that must not be parsed again.
    private static final ThreadLocal<boolean[]> CONTAINED = ThreadLocal.withInitial(() -> new boolean[1]);

    private Placeholders() {
    }

    static void enable(final boolean on, @NotNull final Trust level) {
        enabled = on;
        trust = level;
    }

    public static boolean enabled() {
        return enabled;
    }

    /**
     * Resolves placeholders in text that will be parsed as MiniMessage.
     * Legacy § colors in the values are converted to tags.
     */
    public static @NotNull String apply(@Nullable final Player viewer, @NotNull final String text) {
        if (!enabled || text.indexOf('%') < 0) return text;
        Trust level = trust;
        if (level == Trust.ALL) return Sense.sectionToTags(PlaceholderAPI.setPlaceholders((OfflinePlayer) viewer, text));

        Matcher matcher = TOKEN.matcher(text);
        StringBuilder out = null;
        int at = 0;
        while (matcher.find()) {
            String token = matcher.group();
            String value = PlaceholderAPI.setPlaceholders((OfflinePlayer) viewer, token);
            if (value.equals(token)) continue;
            if (out == null) out = new StringBuilder(text.length() + 32);
            String contained = contain(value, level);
            if (!contained.equals(value)) CONTAINED.get()[0] = true;
            out.append(text, at, matcher.start()).append(Sense.sectionToTags(contained));
            at = matcher.end();
        }
        if (out == null) return text;
        return out.append(text, at, text.length()).toString();
    }

    // Returns whether any value had tags escaped since the last call, and resets the flag.
    static boolean takeContained() {
        boolean[] flag = CONTAINED.get();
        boolean was = flag[0];
        flag[0] = false;
        return was;
    }

    /**
     * Resolves placeholders exactly as PlaceholderAPI returns them, for console commands and conditions
     * where MiniMessage tags have no meaning.
     */
    public static @NotNull String raw(@Nullable final Player viewer, @NotNull final String text) {
        if (!enabled || text.indexOf('%') < 0) return text;
        return PlaceholderAPI.setPlaceholders((OfflinePlayer) viewer, text);
    }

    // Escapes every tag a value isn't trusted to use, along with backslashes, which could otherwise escape the escape.
    static @NotNull String contain(@NotNull final String value, @NotNull final Trust level) {
        if (value.indexOf('<') < 0 && value.indexOf('\\') < 0) return value;
        if (level == Trust.NONE) return Literal.escape(value);

        String text = value.replace("\\", "\\\\");
        Matcher matcher = TAG.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 8);
        int at = 0;
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean allowed = name.charAt(0) == '#' || FORMATTING.contains(name);
            out.append(text, at, matcher.start()).append(allowed ? "<" : "\\<").append(matcher.group(1)).append(matcher.group(2));
            at = matcher.end();
        }
        // A '<' that does not start a tag name, such as in "< 5", is already read as plain text.
        return out.append(text, at, text.length()).toString();
    }
}
