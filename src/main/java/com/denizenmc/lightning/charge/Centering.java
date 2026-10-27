package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.tag.Faces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pads lines containing <center> with spaces, measured against Minecraft's default font.
final class Centering {

    private static final Pattern CENTER = Pattern.compile("<center(?::(\\d{1,4}))?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BREAKS = Pattern.compile("<(?:n|br|newline)>", Pattern.CASE_INSENSITIVE);
    private static final int SPACE = 4;

    private Centering() {
    }

    static @NotNull String apply(
            @NotNull final String text,
            final int defaultCenter,
            @NotNull final Function<String, Component> parse
    ) {
        if (!CENTER.matcher(text).find()) return text;

        String[] lines = BREAKS.matcher(text).replaceAll("\n").split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 64);

        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');

            Matcher matcher = CENTER.matcher(lines[i]);
            if (!matcher.find()) {
                out.append(lines[i]);
                continue;
            }

            int center = matcher.group(1) == null
                    ? defaultCenter
                    : Integer.parseInt(matcher.group(1));

            String line = CENTER.matcher(lines[i]).replaceAll("");
            int padding = (center - width(parse.apply(line), false) / 2) / SPACE;

            if (padding > 0) out.append(" ".repeat(padding));
            out.append(line);
        }

        return out.toString();
    }

    static int width(@NotNull final Component component, final boolean parentBold) {
        TextDecoration.State state = component.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE
                || (state == TextDecoration.State.NOT_SET && parentBold);

        int width = 0;

        if (component instanceof TextComponent text) {
            String content = text.content();

            for (int i = 0; i < content.length(); i++) {
                width += glyph(content.charAt(i)) + (bold ? 1 : 0);
            }
        } else if (Faces.isObject(component)) {
            width += 9;
        }

        for (Component child : component.children()) {
            width += width(child, bold);
        }

        return width;
    }

    // Character widths include the one-pixel gap after each glyph.
    private static int glyph(final char c) {
        return switch (c) {
            case '!', ',', '.', ':', ';', 'i', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case 'I', '[', ']', 't', ' ' -> 4;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '*', '"' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}