package com.denizenmc.lightning.spark;

import com.denizenmc.lightning.Durations;
import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.Yaml;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Loads and validates reusable Sparks from YAML files.
public final class Sparks {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");

    private final Lightning plugin;
    private volatile Map<String, Spark> sparks = Map.of();

    public Sparks(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public @Nullable Spark get(@NotNull final String name) {
        return sparks.get(name);
    }

    public @NotNull Set<String> names() {
        return sparks.keySet();
    }

    public void load() {
        Path folder = plugin.getDataFolder().toPath().resolve("sparks");
        Map<String, Spark> found = new LinkedHashMap<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.walk(folder)) {
                files.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                        .sorted()
                        .forEach(path -> read(folder.relativize(path).toString(), path, found));
            } catch (IOException e) {
                plugin.getLogger().warning("Unable to read the sparks folder: " + e.getMessage());
            }
        }
        sparks = Map.copyOf(found);
    }

    private void read(final String file, final Path path, final Map<String, Spark> found) {
        YamlConfiguration yaml = Yaml.read(path, file, plugin.getLogger());
        for (String key : yaml.getKeys(false)) {
            String name = key.toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()) {
                plugin.getLogger().warning(file + ": Skipping '" + key + "'. Spark names may only contain letters, numbers, dots, dashes, and underscores.");
                continue;
            }
            if (found.containsKey(name)) {
                plugin.getLogger().warning(file + ": Spark '" + name + "' is already defined in another file. The first definition will be used.");
                continue;
            }
            Spark spark = parse(yaml, key, file);
            if (spark != null) found.put(name, spark);
        }
    }

    private @Nullable Spark parse(final YamlConfiguration yaml, final String key, final String file) {
        if (yaml.isString(key)) return new Spark.Text(yaml.getString(key, ""));

        ConfigurationSection section = yaml.getConfigurationSection(key);
        if (section == null) {
            plugin.getLogger().warning(file + ": '" + key + "' must be text or a section defining a type such as color or formula.");
            return null;
        }
        if (section.isString("text")) return new Spark.Text(section.getString("text", ""));
        if (section.isString("color")) return color(section, key, file);
        if (section.isString("formula")) return new Spark.Formula(section.getString("formula", "0"), section.getString("format", "#,##0.##"));
        if (section.isList("cycle") && !section.getStringList("cycle").isEmpty()) {
            return new Spark.Cycle(List.copyOf(section.getStringList("cycle")), Math.max(50L, Durations.millis(section.getString("every"), 1000L)));
        }
        if (section.isList("pick") && !section.getStringList("pick").isEmpty()) return new Spark.Pick(List.copyOf(section.getStringList("pick")));
        if (section.isConfigurationSection("locale")) return translated(section.getConfigurationSection("locale"));
        if (section.isList("permission")) return ranked(section, key, file);
        if (section.isString("scale")) return scale(section, key, file);

        plugin.getLogger().warning(file + ": '" + key + "' must define text, color, formula, scale, cycle, pick, locale, or permission.");
        return null;
    }

    private Spark translated(final ConfigurationSection section) {
        Map<String, String> texts = new HashMap<>();
        for (String locale : section.getKeys(false)) {
            texts.put(locale.toLowerCase(Locale.ROOT).replace('-', '_'), section.getString(locale, ""));
        }
        return new Spark.Translated(Map.copyOf(texts));
    }

    private Spark scale(final ConfigurationSection section, final String key, final String file) {
        List<Spark.Step> steps = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("steps")) {
            Object below = entry.get("below");
            Object text = entry.get("text");
            if (!(below instanceof Number limit) || text == null) {
                plugin.getLogger().warning(file + ": '" + key + "' has a scale step missing its numeric 'below' value or its 'text' value.");
                continue;
            }
            steps.add(new Spark.Step(limit.doubleValue(), text.toString()));
        }
        return new Spark.Scale(section.getString("scale", "0"), List.copyOf(steps), section.getString("else", "{value}"), section.getString("format", "#,##0.##"));
    }

    private @Nullable Spark ranked(final ConfigurationSection section, final String key, final String file) {
        List<Spark.Tier> tiers = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("permission")) {
            Object permission = entry.get("has");
            Object text = entry.get("text");
            if (permission == null || text == null) {
                plugin.getLogger().warning(file + ": '" + key + "' has a permission entry missing its 'has' or 'text' value.");
                continue;
            }
            tiers.add(new Spark.Tier(permission.toString(), text.toString()));
        }
        return new Spark.Ranked(List.copyOf(tiers), section.getString("default", ""));
    }

    private @Nullable Spark color(final ConfigurationSection section, final String key, final String file) {
        String raw = section.getString("color", "").trim();
        TextColor color = raw.startsWith("#") ? TextColor.fromHexString(raw) : NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
        if (color == null) {
            plugin.getLogger().warning(file + ": '" + key + "' uses an unknown color: " + raw + ".");
            return null;
        }

        List<StyleBuilderApplicable> style = new ArrayList<>();
        style.add(color);
        for (String decoration : section.getStringList("style")) {
            TextDecoration found = TextDecoration.NAMES.value(decoration.toLowerCase(Locale.ROOT));
            if (found != null) style.add(found);
            else plugin.getLogger().warning(file + ": '" + key + "' uses an unknown text style: " + decoration + ".");
        }
        return new Spark.Color(Tag.styling(style.toArray(StyleBuilderApplicable[]::new)));
    }
}