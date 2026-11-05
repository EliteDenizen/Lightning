package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.tag.Faces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

// Checks whether text needs processing without parsing it.
final class Sense {

    private Sense() {
    }

    // Plugin-generated components rarely contain literal '<' characters, so a tree walk
    // is enough to determine whether MiniMessage processing or placeholder expansion is needed.
    static boolean worthLooking(@NotNull final Component component, final boolean placeholders) {
        if (component instanceof TextComponent text) {
            String content = text.content();
            if (content.indexOf('<') >= 0 || (placeholders && content.indexOf('%') >= 0)) return true;
        } else if (component instanceof TranslatableComponent translatable) {
            for (TranslationArgument argument : translatable.arguments()) {
                if (worthLooking(argument.asComponent(), placeholders)) return true;
            }
        }
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.value() instanceof Component text && worthLooking(text, placeholders)) return true;
        for (Component child : component.children()) {
            if (worthLooking(child, placeholders)) return true;
        }
        return false;
    }

    // MiniMessage escapes literal '<' characters as "\<". This lets us detect tags
    // explicitly written in configuration without treating ordinary text as markup.
    static boolean hasWrittenTag(@NotNull final String serialized, @NotNull final TagResolver resolver) {
        int index = serialized.indexOf("\\<");
        while (index >= 0) {
            // A backslash written before the tag escapes it, so it stays literal text.
            if (index > 0 && serialized.charAt(index - 1) == '\\') {
                index = serialized.indexOf("\\<", index + 2);
                continue;
            }
            int start = index + 2;
            if (start < serialized.length() && (serialized.charAt(start) == '/' || serialized.charAt(start) == '!')) start++;
            int end = start;
            while (end < serialized.length()) {
                char c = serialized.charAt(end);
                if (c == ':' || c == '>' || c == ' ' || c == '<' || c == '\\') break;
                end++;
            }
            if (end > start) {
                String name = serialized.substring(start, end).toLowerCase(Locale.ROOT);
                if (name.charAt(0) == '#' ? isHex(name) : resolver.has(name)) return true;
            }
            index = serialized.indexOf("\\<", end);
        }
        return false;
    }

    static boolean hasPlaceholder(@NotNull final String text) {
        int open = text.indexOf('%');
        while (open >= 0) {
            int close = text.indexOf('%', open + 1);
            if (close < 0) return false;
            if (close > open + 1 && text.substring(open + 1, close).indexOf(' ') < 0) return true;
            open = close;
        }
        return false;
    }

    // Turns text written as "<tag>" back into a live tag, while keeping text the author or a player
    // escaped as "\<tag>" literal. The serializer writes a content backslash as "\\" and '<' as "\<",
    // so "\\\<" is a backslash written before '<' and must stay escaped. Replacing every "\<" would
    // turn it into "\\<", a literal backslash followed by a live tag, which bypasses escaping.
    static @NotNull String unescape(@NotNull final String serialized) {
        if (serialized.indexOf('\\') < 0) return serialized;
        StringBuilder out = new StringBuilder(serialized.length());
        int length = serialized.length();
        for (int i = 0; i < length; i++) {
            char c = serialized.charAt(i);
            if (c == '\\' && i + 1 < length) {
                char next = serialized.charAt(i + 1);
                if (next == '<') {
                    out.append('<');
                    i++;
                    continue;
                }
                if (next == '\\') {
                    if (i + 3 < length && serialized.charAt(i + 2) == '\\' && serialized.charAt(i + 3) == '<') {
                        out.append("\\<");
                        i += 3;
                    } else {
                        out.append("\\\\");
                        i++;
                    }
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    // Converts legacy section-sign formatting codes, often returned by PlaceholderAPI,
    // into MiniMessage tags.
    static @NotNull String sectionToTags(@NotNull final String text) {
        if (text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'x') {
                String hex = sectionHex(text, i);
                if (hex != null) {
                    out.append("<#").append(hex).append('>');
                    i += 13;
                    continue;
                }
            }
            String tag = legacyTag(code);
            if (tag == null) {
                out.append(c);
                continue;
            }
            out.append(tag);
            i++;
        }
        return out.toString();
    }

    // Reads the six digits of a "§x§R§R§G§G§B§B" color, or returns null when the sequence is malformed.
    private static String sectionHex(final String text, final int at) {
        if (at + 13 >= text.length()) return null;
        StringBuilder hex = new StringBuilder(6);
        for (int marker = at + 2; marker <= at + 12; marker += 2) {
            char digit = text.charAt(marker + 1);
            if (text.charAt(marker) != '§' || Character.digit(digit, 16) < 0) return null;
            hex.append(digit);
        }
        return hex.toString();
    }

    private static String legacyTag(final char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private static boolean isHex(final String name) {
        if (name.length() != 7) return false;
        for (int i = 1; i < 7; i++) {
            if (Character.digit(name.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}