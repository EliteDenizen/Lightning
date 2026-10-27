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

    static @NotNull String unescape(@NotNull final String serialized) {
        return serialized.replace("\\<", "<");
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
            if (code == 'x' && i + 13 < text.length()) {
                StringBuilder hex = new StringBuilder(6);
                for (int digit = i + 3; digit <= i + 13; digit += 2) hex.append(text.charAt(digit));
                out.append("<#").append(hex).append('>');
                i += 13;
                continue;
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