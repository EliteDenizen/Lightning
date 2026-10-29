package com.denizenmc.lightning.rewire;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.Yaml;
import com.denizenmc.lightning.charge.Surface;
import com.denizenmc.lightning.charge.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

// Rewrites messages from other plugins and vanilla minecraft without modifying their original source.
public final class Rewires {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[^}]*}");

    private final Lightning plugin;
    private volatile Map<Surface, List<Rewire>> rules = Map.of();
    private volatile int size;

    public Rewires(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public int size() {
        return size;
    }

    public @Nullable Match match(@NotNull final Component input, @NotNull final Surface surface, @Nullable final Player viewer) {
        List<Rewire> candidates = rules.get(surface);
        if (candidates == null) return null;

        String plain = null;
        List<TranslatableComponent> translations = null;
        for (Rewire rule : candidates) {
            if (rule.partial()) continue;
            List<String> values;
            try {
                if (rule.kind() == Rewire.Kind.LANG) {
                    if (translations == null) translations = translations(input, new ArrayList<>());
                    values = arguments(translations, rule.pattern());
                    // Some messages, such as ban screens, arrive as plain English instead of translation components.
                    if (values == null && rule.regex() != null) {
                        if (plain == null) plain = plain(input);
                        values = rule.translated(plain);
                    }
                } else {
                    if (plain == null) plain = plain(input);
                    values = rule.test(plain);
                }
            } catch (IllegalStateException e) {
                plugin.debug("Rewire '" + rule.name() + "' was skipped because it took too long to match a message.");
                continue;
            }
            if (values == null) continue;
            if (rule.drop()) return new Match(rule.name(), null, false);

            // Placeholders are resolved on the configured text before message values are inserted,
            // so text taken from a message is never read by PlaceholderAPI.
            String template = rule.to();
            boolean placeholders = Placeholders.enabled() && template.indexOf('%') >= 0;
            if (placeholders) template = Placeholders.apply(viewer, template);
            return new Match(rule.name(), fill(template, values, input), placeholders);
        }
        return null;
    }

    /**
     * Applies partial replacements while preserving the surrounding component structure.
     * Matches cannot span text split across components with different formatting.
     */
    public @NotNull Patched patch(@NotNull final Component input, @NotNull final Surface surface, @Nullable final Player viewer) {
        List<Rewire> candidates = rules.get(surface);
        if (candidates == null) return new Patched(input, false, false);

        Patched patched = apply(input, input, candidates, viewer);
        if (patched.changed() || !hasTranslation(input)) return patched;

        // Render vanilla translations in English so their visible text can be matched.
        // The English version is used only when a rule changes the message.
        Component english;
        try {
            english = GlobalTranslator.render(input, Locale.US);
        } catch (Throwable e) {
            return patched;
        }
        return english.equals(input) ? patched : apply(english, input, candidates, viewer);
    }

    private Patched apply(final Component start, final Component original, final List<Rewire> candidates, final Player viewer) {
        Component output = start;
        boolean live = false;
        boolean touched = false;
        String plain = null;
        for (Rewire rule : candidates) {
            if (!rule.partial()) continue;
            // A quick check on the plain text skips rules that cannot match before the component is rebuilt.
            if (plain == null) plain = PLAIN.serialize(output);
            try {
                if (!rule.regex().matcher(new Bounded(plain)).find()) continue;
            } catch (IllegalStateException e) {
                plugin.debug("Rewire '" + rule.name() + "' was skipped because it took too long to match a message.");
                continue;
            }
            String template = Placeholders.enabled() && rule.to() != null && rule.to().indexOf('%') >= 0
                    ? Placeholders.apply(viewer, rule.to())
                    : rule.to();
            Component before = output;
            output = output.replaceText(TextReplacementConfig.builder()
                    .match(rule.regex())
                    .replacement((match, builder) -> {
                        if (rule.drop()) return Component.empty();
                        List<String> values = new ArrayList<>(match.groupCount() + 1);
                        for (int group = 0; group <= match.groupCount(); group++) {
                            String value = match.group(group);
                            values.add(value == null ? "" : value.replace("<", "\\<"));
                        }
                        return plugin.charger().render(fill(template, values, original), viewer);
                    })
                    .build());
            if (!output.equals(before)) {
                plain = null;
                touched = true;
                if (!rule.drop()) live |= lively(rule.to());
            }
        }
        return touched ? new Patched(output, true, live) : new Patched(original, false, false);
    }

    private static boolean hasTranslation(final Component component) {
        if (component instanceof TranslatableComponent) return true;
        for (Component child : component.children()) {
            if (hasTranslation(child)) return true;
        }
        return false;
    }

    // These tags can produce viewer-specific or changing output, so their results must be refreshed.
    private static boolean lively(final String to) {
        return to.indexOf('%') >= 0 || to.contains("<face") || to.contains("<spark:");
    }

    public record Patched(@NotNull Component component, boolean changed, boolean live) {
    }

    public int watching(@NotNull final Surface surface) {
        List<Rewire> list = rules.get(surface);
        return list == null ? 0 : list.size();
    }

    // Render translations in English before matching so rules can inspect the text players see.
    private static String plain(final Component input) {
        if (!hasTranslation(input)) return PLAIN.serialize(input);
        try {
            return PLAIN.serialize(GlobalTranslator.render(input, Locale.US));
        } catch (Throwable e) {
            return PLAIN.serialize(input);
        }
    }

    public void load() {
        Path folder = plugin.getDataFolder().toPath().resolve("rewires");
        Map<Surface, List<Rewire>> found = new EnumMap<>(Surface.class);
        int count = 0;
        if (Files.isDirectory(folder)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(folder)) {
                files = walk.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")).sorted().toList();
            } catch (IOException e) {
                plugin.getLogger().warning("Unable to read the rewires folder: " + e.getMessage());
                files = List.of();
            }
            for (Path path : files) count += read(folder.relativize(path).toString(), path, found);
        }

        Map<Surface, List<Rewire>> frozen = new EnumMap<>(Surface.class);
        found.forEach((surface, list) -> frozen.put(surface, List.copyOf(list)));
        rules = Collections.unmodifiableMap(frozen);
        size = count;
    }

    private int read(final String file, final Path path, final Map<Surface, List<Rewire>> found) {
        YamlConfiguration yaml = Yaml.read(path, file, plugin.getLogger());
        int count = 0;
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            Rewire rule = section == null ? null : parse(name, section, file);
            if (rule == null) continue;
            for (Surface surface : rule.surfaces()) found.computeIfAbsent(surface, key -> new ArrayList<>()).add(rule);
            plugin.debug("Rewire '" + name + "' from " + file + " watches " + rule.surfaces() + (rule.kind() == Rewire.Kind.LANG
                    ? (rule.regex() == null ? ", no English text found for " + rule.pattern() : ", English text found for " + rule.pattern()) : ""));
            count++;
        }
        return count;
    }

    private @Nullable Rewire parse(final String name, final ConfigurationSection section, final String file) {
        String to = section.getBoolean("drop", false) ? null : section.getString("to");
        if (to == null && !section.getBoolean("drop", false)) {
            plugin.getLogger().warning(file + ": rewire '" + name + "' requires 'to' or 'drop: true'.");
            return null;
        }

        Set<Surface> surfaces = EnumSet.noneOf(Surface.class);
        // YAML may interpret an unquoted "on" key as boolean true, so accept both key names.
        List<String> on = section.getStringList("surfaces");
        if (on.isEmpty() && section.isList("true")) on = section.getStringList("true");
        if (on.isEmpty()) surfaces.add(Surface.CHAT);
        for (String key : on) {
            Surface surface = Surface.byKey(key);
            if (surface != null) surfaces.add(surface);
            else plugin.getLogger().warning(file + ": rewire '" + name + "' references an unknown surface: " + key + ".");
        }

        if (section.isString("lang")) {
            String key = section.getString("lang", "");
            int[] arity = new int[1];
            return new Rewire(name, Rewire.Kind.LANG, key, english(key, arity), arity[0], to, surfaces);
        }
        if (section.isString("replace")) {
            String text = section.getString("replace", "");
            if (text.isEmpty()) {
                plugin.getLogger().warning(file + ": rewire '" + name + "' has an empty replacement target.");
                return null;
            }
            return new Rewire(name, Rewire.Kind.REPLACE, text, Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE), 0, to, surfaces);
        }
        if (section.isString("pattern")) {
            try {
                String regex = section.getString("pattern", "");
                return new Rewire(name, Rewire.Kind.PATTERN, regex, Pattern.compile(regex), 0, to, surfaces);
            } catch (PatternSyntaxException e) {
                plugin.getLogger().warning(file + ": rewire '" + name + "' has an invalid pattern: " + e.getDescription() + ".");
                return null;
            }
        }
        if (section.isString("text")) return new Rewire(name, Rewire.Kind.TEXT, section.getString("text", "").strip(), null, 0, to, surfaces);
        if (section.isString("contains")) {
            return new Rewire(name, Rewire.Kind.CONTAINS, section.getString("contains", "").toLowerCase(Locale.ROOT), null, 0, to, surfaces);
        }
        if (section.isString("regex")) {
            try {
                String regex = section.getString("regex", "");
                return new Rewire(name, Rewire.Kind.REGEX, regex, Pattern.compile(regex), 0, to, surfaces);
            } catch (PatternSyntaxException e) {
                plugin.getLogger().warning(file + ": rewire '" + name + "' has an invalid regex: " + e.getDescription() + ".");
                return null;
            }
        }
        plugin.getLogger().warning(file + ": rewire '" + name + "' requires one of: text, contains, regex, lang, replace, or pattern.");
        return null;
    }

    // Converts a translated message template into a regex that captures its arguments.
    // Returns null when no English translation is available for the key.
    private static @Nullable Pattern english(final String key, final int[] arity) {
        MessageFormat format;
        try {
            format = GlobalTranslator.translator().translate(key, Locale.US);
        } catch (Throwable e) {
            return null;
        }
        if (format == null) return null;

        String template = format.toPattern();
        Matcher argument = ARGUMENT.matcher(template);
        StringBuilder regex = new StringBuilder("^\\s*");
        Set<Integer> seen = new HashSet<>();
        int last = 0;
        int highest = -1;
        while (argument.find()) {
            regex.append(Pattern.quote(literal(template.substring(last, argument.start()))));
            int index = Integer.parseInt(argument.group(1));
            regex.append(seen.add(index) ? "(?<a" + index + ">.*?)" : "\\k<a" + index + ">");
            highest = Math.max(highest, index);
            last = argument.end();
        }
        regex.append(Pattern.quote(literal(template.substring(last)))).append("\\s*$");
        arity[0] = highest + 1;
        try {
            return Pattern.compile(regex.toString(), Pattern.DOTALL);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    // MessageFormat uses doubled apostrophes and apostrophes to mark literal sections.
    private static String literal(final String text) {
        return text.replace("''", "\u0000").replace("'", "").replace("\u0000", "'");
    }

    private static List<TranslatableComponent> translations(final Component component, final List<TranslatableComponent> out) {
        if (component instanceof TranslatableComponent translatable) out.add(translatable);
        for (Component child : component.children()) translations(child, out);
        return out;
    }

    // Includes the serialized translation and its arguments, using zero-based placeholders.
    private static @Nullable List<String> arguments(final List<TranslatableComponent> translations, final String key) {
        for (TranslatableComponent translatable : translations) {
            if (!translatable.key().equals(key)) continue;
            List<String> values = new ArrayList<>();
            values.add(MINI.serialize(translatable));
            for (TranslationArgument argument : translatable.arguments()) values.add(MINI.serialize(argument.asComponent()));
            return values;
        }
        return null;
    }

    // Fills {text}, {0}, {1} and so on in a single pass, so an inserted value is never filled a second time.
    private String fill(final String template, final List<String> values, final Component input) {
        StringBuilder out = new StringBuilder(template.length() + 32);
        int at = 0;
        while (at < template.length()) {
            int open = template.indexOf('{', at);
            int close = open < 0 ? -1 : template.indexOf('}', open);
            if (close < 0) {
                out.append(template, at, template.length());
                break;
            }
            String key = template.substring(open + 1, close);
            String value = null;
            if (key.equals("text")) {
                value = plugin.charger().source(input);
            } else if (!key.isEmpty() && key.chars().allMatch(Character::isDigit) && key.length() < 4) {
                int index = Integer.parseInt(key);
                if (index < values.size()) value = values.get(index);
            }
            if (value == null) {
                out.append(template, at, open + 1);
                at = open + 1;
                continue;
            }
            out.append(template, at, open).append(value);
            at = close + 1;
        }
        return out.toString();
    }

    public record Match(@NotNull String rule, @Nullable String text, boolean placeholders) {

        public boolean drop() {
            return text == null;
        }
    }
}