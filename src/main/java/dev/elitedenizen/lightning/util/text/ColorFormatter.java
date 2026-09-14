package dev.elitedenizen.lightning.util.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_OUT = LegacyComponentSerializer.builder()
            .character('\u00a7')
            .hexColors()
            .build();

    private static final Pattern LEGACY_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern SMALL_CAPS_TAG = Pattern.compile("<smallcaps>(.*?)</smallcaps>", Pattern.DOTALL);

    private ColorFormatter() {}

    public static String colorize(String text) {
        return colorize(text, null);
    }

    public static String colorize(String text, Player player) {
        if (text == null || text.isEmpty()) return text;
        return LEGACY_OUT.serialize(parse(text, player));
    }

    public static Component parse(String text) {
        return parse(text, null);
    }

    public static Component parse(String text, Player player) {
        if (text == null || text.isEmpty()) return Component.empty();
        String converted = toMiniMessageSyntax(text);
        try {
            TagResolver miniPlaceholders = MiniPlaceholdersBridge.resolverFor(player);
            return MINI_MESSAGE.deserialize(converted, miniPlaceholders);
        } catch (Exception e) {
            return Component.text(ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    public static String strip(String text) {
        if (text == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(parse(text));
    }

    private static String toMiniMessageSyntax(String text) {
        String result = SMALL_CAPS_TAG.matcher(text)
                .replaceAll(match -> Matcher.quoteReplacement(toSmallCaps(match.group(1))));

        result = LEGACY_HEX.matcher(result).replaceAll("<#$1>");

        StringBuilder out = new StringBuilder(result.length() + 16);
        Matcher m = LEGACY_CODE.matcher(result);
        int last = 0;
        while (m.find()) {
            out.append(result, last, m.start());
            out.append(namedTagFor(Character.toLowerCase(m.group(1).charAt(0))));
            last = m.end();
        }
        out.append(result.substring(last));
        return out.toString();
    }

    private static String namedTagFor(char code) {
        switch (code) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";
            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return "";
        }
    }

    private static final String[] SMALL_CAPS_LETTERS = {
            "\u1d00", "\u0299", "\u1d04", "\u1d05", "\u1d07", "\ua730", "\u0262", "\u029c",
            "\u026a", "\u1d0a", "\u1d0b", "\u029f", "\u1d0d", "\u0274", "\u1d0f", "\u1d18",
            "\u01eb", "\u0280", "\ua731", "\u1d1b", "\u1d1c", "\u1d20", "\u1d21", "x",
            "\u028f", "\u1d22"
    };

    public static String toSmallCaps(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = Character.toLowerCase(c) - 'a';
            if (index >= 0 && index < SMALL_CAPS_LETTERS.length) {
                out.append(SMALL_CAPS_LETTERS[index]);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String repeat(String s, int times) {
        if (s == null || times <= 0) return "";
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}
