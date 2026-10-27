package com.denizenmc.lightning.tool;

import com.denizenmc.lightning.Lightning;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import com.denizenmc.lightning.charge.Charger;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Scans plugin configuration files for invalid Lightning tags, missing Sparks, and legacy color codes.
public final class Doctor {

    private static final Pattern TAG = Pattern.compile("<(/?!?)([a-zA-Z_][a-zA-Z0-9_]*)(?::([^<>]*))?>");
    private static final Pattern LEGACY = Pattern.compile("[&§](?:#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])");
    private static final List<String> KNOWN = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "grey", "dark_gray",
            "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "color", "colour", "bold", "italic",
            "underlined", "strikethrough", "obfuscated", "reset", "click", "hover", "insert", "rainbow", "gradient", "transition",
            "font", "newline", "lang", "translate", "selector", "score", "shadow", "head", "sprite", "smallcaps", "upper", "lower",
            "charge", "center", "clock", "face", "spark", "actionbar", "title", "sound", "silent");
    private static final int SHOWN = 12;

    private final Lightning plugin;

    public Doctor(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void check(@NotNull final CommandSender sender, @Nullable final String only) {
        sender.sendMessage(plugin.message("checking"));
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            List<Path> folders = ConfigFiles.folders(plugin, only);
            if (folders.isEmpty()) {
                sender.sendMessage(plugin.message("missing", Placeholder.unparsed("name", String.valueOf(only))));
                return;
            }
            List<Finding> findings = new ArrayList<>();
            int files = 0;
            for (Path folder : folders) {
                for (Path file : ConfigFiles.texts(folder)) {
                    files++;
                    scan(folder.getParent().relativize(file).toString(), file, findings);
                }
            }
            report(sender, group(findings), files);
        });
    }

    private void scan(final String name, final Path file, final List<Finding> findings) {
        List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            try {
                lines = java.nio.file.Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            } catch (IOException again) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        int legacy = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.indexOf('<') >= 0) inspect(name, i + 1, line, findings);
            if (line.indexOf('&') >= 0 || line.indexOf('§') >= 0) {
                if (LEGACY.matcher(line).find()) legacy++;
            }
        }
        if (legacy > 0) findings.add(new Finding(name, 0, "", legacy + " lines still use legacy color codes. Run /lightning convert to update them."));
    }

    private void inspect(final String file, final int number, final String line, final List<Finding> findings) {
        Matcher matcher = TAG.matcher(line);
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            if (name.equals("spark")) {
                String argument = matcher.group(3);
                String spark = argument == null ? "" : argument.split(":")[0].replace("'", "").trim().toLowerCase(Locale.ROOT);
                if (!spark.isEmpty() && !spark.contains("%") && plugin.sparks().get(spark) == null) {
                    findings.add(new Finding(file, number, line.trim(), "Unknown Spark '" + spark + "'"));
                }
                continue;
            }
            if (plugin.charger().knows(name) || plugin.sparks().get(name) != null) continue;

            // Report likely misspellings while ignoring tags that may belong to the original plugin.
            String guess = closest(name);
            if (guess != null) findings.add(new Finding(file, number, line.trim(), "Unknown tag <" + name + ">. Did you mean <" + guess + ">?"));
        }
    }

    private void report(final CommandSender sender, final List<Finding> findings, final int files) {
        sender.sendMessage(plugin.message("checked",
                Placeholder.unparsed("files", String.valueOf(files)),
                Placeholder.unparsed("found", String.valueOf(findings.size()))));

        for (int i = 0; i < Math.min(SHOWN, findings.size()); i++) {
            Finding finding = findings.get(i);
            String where = finding.line() == 0 ? finding.file() : finding.file() + ":" + finding.line();
            Component hover = finding.text().isEmpty()
                    ? Component.text("Click to copy the file path", NamedTextColor.GRAY)
                    : Component.text(finding.text(), NamedTextColor.GRAY).append(Component.newline()).append(Component.text("Click to copy where it is", NamedTextColor.DARK_GRAY));

            // Display source lines as literal text and make the corresponding file location easy to copy.
            sender.sendMessage(Charger.own(Component.text("  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(where, TextColor.color(0xFFD36E)))
                    .append(Component.text("  " + finding.problem(), TextColor.color(0xCFCFCF)))
                    .hoverEvent(hover)
                    .clickEvent(ClickEvent.copyToClipboard(where))));
        }

        if (findings.size() > SHOWN) {
            Path report = plugin.getDataFolder().toPath().resolve("check.txt");
            List<String> lines = findings.stream().map(finding -> finding.file() + ":" + finding.line() + "  " + finding.problem() + "  " + finding.text()).toList();
            try {
                java.nio.file.Files.write(report, lines, StandardCharsets.UTF_8);
                sender.sendMessage(plugin.message("more",
                        Placeholder.unparsed("count", String.valueOf(findings.size() - SHOWN))));
            } catch (IOException e) {
                plugin.getLogger().warning("\"Unable to write check.txt: " + e.getMessage());
            }
        }
    }

    // Combine identical findings within a file and report how many lines are affected.
    private static List<Finding> group(final List<Finding> findings) {
        java.util.Map<String, Finding> grouped = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (Finding finding : findings) {
            String key = finding.file() + "|" + finding.problem();
            grouped.putIfAbsent(key, finding);
            counts.merge(key, 1, Integer::sum);
        }
        List<Finding> result = new ArrayList<>(grouped.size());
        grouped.forEach((key, first) -> {
            int count = counts.get(key);
            result.add(count == 1 ? first : new Finding(first.file(), first.line(), first.text(), first.problem() + ", on " + count + " lines"));
        });
        return result;
    }

    private static @Nullable String closest(final String name) {
        if (name.length() < 3) return null;
        String best = null;
        int bestDistance = 3;
        for (String known : KNOWN) {
            int distance = distance(name, known);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = known;
            }
        }
        return best;
    }

    private static int distance(final String a, final String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private record Finding(String file, int line, String text, String problem) {
    }
}
