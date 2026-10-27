package com.denizenmc.lightning.tool;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts legacy color codes in plugin files to MiniMessage tags and creates backups when applying changes.
 */
public final class Converter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Lightning plugin;

    public Converter(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void convert(@NotNull final CommandSender sender, @NotNull final String name, final boolean apply) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            List<Path> folders = ConfigFiles.folders(plugin, name);
            if (folders.isEmpty() || folders.get(0).equals(plugin.getDataFolder().toPath())) {
                sender.sendMessage(plugin.message("missing", Placeholder.unparsed("name", name)));
                return;
            }
            Path folder = folders.get(0);
            Path backup = plugin.getDataFolder().toPath().resolve("backups").resolve(folder.getFileName() + "-" + LocalDateTime.now().format(STAMP));

            int files = 0;
            int lines = 0;
            List<String[]> samples = new ArrayList<>();
            for (Path file : ConfigFiles.texts(folder)) {
                // ISO-8859-1 maps each byte to one character, preserving untouched bytes when writing the file back.
                String original;
                try {
                    original = java.nio.file.Files.readString(file, StandardCharsets.ISO_8859_1);
                } catch (IOException e) {
                    continue;
                }

                String[] before = original.split("\n", -1);
                StringBuilder after = new StringBuilder(original.length() + 64);
                int changed = 0;
                for (int i = 0; i < before.length; i++) {
                    String converted = line(before[i]);
                    if (!converted.equals(before[i])) {
                        changed++;
                        if (samples.size() < 3) samples.add(new String[]{before[i].trim(), converted.trim()});
                    }
                    after.append(converted);
                    if (i < before.length - 1) after.append('\n');
                }
                if (changed == 0) continue;
                files++;
                lines += changed;

                if (!apply) continue;
                try {
                    Path copy = backup.resolve(folder.relativize(file));
                    java.nio.file.Files.createDirectories(copy.getParent());
                    java.nio.file.Files.copy(file, copy);
                    java.nio.file.Files.writeString(file, after.toString(), StandardCharsets.ISO_8859_1);
                } catch (IOException e) {
                    plugin.getLogger().warning("Unable to convert " + file + ": " + e.getMessage());
                }
            }

            String target = folder.getFileName().toString();
            if (lines == 0) {
                sender.sendMessage(plugin.message("clean", Placeholder.unparsed("name", target)));
                return;
            }

            Component summary = plugin.message(apply ? "converted" : "preview",
                    Placeholder.unparsed("name", target),
                    Placeholder.unparsed("files", String.valueOf(files)),
                    Placeholder.unparsed("lines", String.valueOf(lines)));
            Component buttons = Component.text(" ").append(examples(samples));
            if (!apply) buttons = buttons.append(Component.space()).append(button(plugin.message("apply"),
                    Component.text("Click to convert " + target, NamedTextColor.GRAY),
                    ClickEvent.runCommand("/lightning convert " + target + " confirm")));
            // Example tags are displayed as plain text so Lightning does not process them again.
            sender.sendMessage(Charger.own(summary.append(buttons)));
        });
    }

    private Component examples(final List<String[]> samples) {
        Component hover = Component.empty();
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) hover = hover.append(Component.newline()).append(Component.newline());
            hover = hover.append(Component.text("Before  ", NamedTextColor.DARK_GRAY)).append(Component.text(samples.get(i)[0], NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("After   ", NamedTextColor.DARK_GRAY)).append(Component.text(samples.get(i)[1], NamedTextColor.WHITE));
        }
        return button(plugin.message("examples"), hover, null);
    }

    private static Component button(final Component label, final Component hover, final ClickEvent click) {
        Component button = label.hoverEvent(hover);
        return click == null ? button : button.clickEvent(click);
    }

    // Legacy color codes also clear active formatting, so insert <reset> before a new color when formatting is active.
    static @NotNull String line(@NotNull final String line) {
        if (line.indexOf('&') < 0 && line.indexOf('§') < 0) return line;
        StringBuilder out = new StringBuilder(line.length() + 32);
        boolean decorated = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c != '&' && c != '§') || i + 1 >= line.length() || insideLink(line, i)) {
                out.append(c);
                continue;
            }

            char code = Character.toLowerCase(line.charAt(i + 1));
            String hex = hex(line, i);
            if (hex != null) {
                if (decorated) out.append("<reset>");
                decorated = false;
                out.append("<#").append(hex).append('>');
                i += line.charAt(i + 1) == '#' ? 7 : 13;
                continue;
            }

            String tag = tag(code);
            boolean query = i + 2 < line.length() && line.charAt(i + 2) == '=';
            if (tag == null || query) {
                out.append(c);
                continue;
            }
            if (Character.digit(code, 16) >= 0) {
                if (decorated) out.append("<reset>");
                decorated = false;
            } else if (code == 'r') {
                decorated = false;
            } else {
                decorated = true;
            }
            out.append(tag);
            i++;
        }
        return out.toString();
    }

    private static String hex(final String line, final int at) {
        if (at + 7 < line.length() && line.charAt(at + 1) == '#') {
            String value = line.substring(at + 2, Math.min(line.length(), at + 8));
            if (value.length() == 6 && value.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) return value.toLowerCase(Locale.ROOT);
        }
        if (at + 13 < line.length() && Character.toLowerCase(line.charAt(at + 1)) == 'x') {
            StringBuilder value = new StringBuilder(6);
            for (int j = at + 2; j <= at + 13; j += 2) {
                char marker = line.charAt(j);
                char digit = line.charAt(j + 1);
                if ((marker != '&' && marker != '§') || Character.digit(digit, 16) < 0) return null;
                value.append(digit);
            }
            return value.toString().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean insideLink(final String line, final int at) {
        int start = at;
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1)) && line.charAt(start - 1) != '"' && line.charAt(start - 1) != '\'') start--;
        return line.substring(start, at).contains("://");
    }

    private static String tag(final char code) {
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
}