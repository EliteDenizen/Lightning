package com.denizenmc.lightning.custom;

import com.denizenmc.lightning.Durations;
import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.Yaml;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
import java.util.UUID;
import java.util.stream.Stream;

// Loads command definitions from commands/ and registers them with Brigadier.
public final class CommandBook {

    private final Lightning plugin;
    private final Ledger ledger;
    private final Runner runner;
    private volatile Map<String, Spec> commands = Map.of();

    public CommandBook(@NotNull final Lightning plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
        this.runner = new Runner(plugin, ledger);
    }

    public int size() {
        return commands.size();
    }

    public void load() {
        Path folder = plugin.getDataFolder().toPath().resolve("commands");
        Map<String, Spec> found = new LinkedHashMap<>();
        if (Files.isDirectory(folder)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(folder)) {
                files = walk.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")).sorted().toList();
            } catch (IOException e) {
                plugin.getLogger().warning("The commands folder couldn't be read: " + e.getMessage());
                files = List.of();
            }
            for (Path path : files) read(folder.relativize(path).toString(), path, found);
        }
        commands = Map.copyOf(found);
        ledger.load();
    }

    public void save() {
        ledger.save();
    }

    public void forget(@NotNull final UUID player) {
        ledger.reset(player);
    }

    // Reloads update existing command behavior. New commands and arguments require a restart
    // because players receive command declarations when they join.
    public void register(@NotNull final Commands registrar) {
        for (Spec spec : commands.values()) {
            Set<String> taken = registrar.register(literal(spec.name(), spec, spec.name()).build(),
                    spec.description().isEmpty() ? null : spec.description(), spec.aliases());
            if (!taken.contains(spec.name())) {
                plugin.getLogger().warning("/" + spec.name() + " is taken by another plugin, use /lightning:" + spec.name() + " for yours.");
            }
        }
    }

    private void read(final String file, final Path path, final Map<String, Spec> found) {
        YamlConfiguration yaml = Yaml.read(path, file, plugin.getLogger());
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            String name = key.toLowerCase(Locale.ROOT);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            if (!name.matches("[a-z0-9_-]+")) {
                plugin.getLogger().warning(file + ": skipped '" + key + "', command names can only use letters, numbers, dashes and underscores.");
                continue;
            }
            if (found.containsKey(name)) {
                plugin.getLogger().warning(file + ": /" + name + " is already in another file, the first one stays.");
                continue;
            }
            found.put(name, parse(name, name, section, file, Spec.Sender.ANY));
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> literal(final String word, final Spec spec, final String label) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(word);
        if (spec.permission() != null) node.requires(source -> source.getSender().hasPermission(spec.permission()));
        for (Spec child : spec.subcommands().values()) node.then(literal(child.name(), child, label + " " + child.name()));

        node.executes(context -> {
            if (spec.required() > 0 || (spec.actions().isEmpty() && !spec.subcommands().isEmpty())) {
                runner.notify(context.getSource().getSender(), live(spec), "usage", Placeholder.unparsed("usage", live(spec).usage(label)));
                return 0;
            }
            return execute(spec, context, 0);
        });
        attach(node, spec, 0);
        return node;
    }

    private void attach(final ArgumentBuilder<CommandSourceStack, ?> parent, final Spec spec, final int index) {
        if (index >= spec.args().size()) return;
        Arg arg = spec.args().get(index);
        var node = Commands.argument(arg.name(), arg.type());
        if (arg.kind() == Arg.Kind.CHOICE) {
            node.suggests((context, builder) -> {
                for (String option : arg.options()) {
                    if (option.startsWith(builder.getRemainingLowerCase())) builder.suggest(option);
                }
                return builder.buildFuture();
            });
        }
        if (index + 1 >= spec.required()) node.executes(context -> execute(spec, context, index + 1));
        attach(node, spec, index + 1);
        parent.then(node);
    }

    private int execute(final Spec spec, final CommandContext<CommandSourceStack> context, final int provided) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        Map<String, String> values = values(spec, context, provided, sender);
        if (values == null) return 0;
        runner.run(live(spec), sender, values);
        return 1;
    }

    private @Nullable Map<String, String> values(final Spec spec, final CommandContext<CommandSourceStack> context, final int provided,
                                                 final CommandSender sender) throws CommandSyntaxException {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < spec.args().size(); i++) {
            Arg arg = spec.args().get(i);
            if (i >= provided) {
                values.put(arg.name(), arg.fallback() == null ? "" : arg.fallback());
                continue;
            }
            String value = switch (arg.kind()) {
                case PLAYER -> {
                    List<Player> players = context.getArgument(arg.name(), PlayerSelectorArgumentResolver.class).resolve(context.getSource());
                    yield players.isEmpty() ? null : players.get(0).getName();
                }
                case NUMBER -> String.valueOf(context.getArgument(arg.name(), Integer.class));
                case DECIMAL -> String.valueOf(context.getArgument(arg.name(), Double.class));
                case BOOLEAN -> String.valueOf(context.getArgument(arg.name(), Boolean.class));
                case WORLD -> context.getArgument(arg.name(), World.class).getName();
                default -> context.getArgument(arg.name(), String.class);
            };
            if (value == null) {
                runner.notify(sender, live(spec), "offline", Placeholder.unparsed("name", arg.name()));
                return null;
            }
            if (arg.kind() == Arg.Kind.CHOICE && !arg.options().contains(value.toLowerCase(Locale.ROOT))) {
                runner.notify(sender, live(spec), "choice", Placeholder.unparsed("choices", String.join(", ", arg.options())));
                return null;
            }
            values.put(arg.name(), value);
        }
        return values;
    }

    // The registered argument tree stays fixed until restart, but command behavior uses the latest loaded definition.
    private Spec live(final Spec built) {
        String[] parts = built.path().split("\\.");
        Spec spec = commands.get(parts[0]);
        for (int i = 1; i < parts.length && spec != null; i++) spec = spec.subcommands().get(parts[i]);
        return spec == null ? built : spec;
    }

    private Spec parse(final String path, final String name, final ConfigurationSection section, final String file, final Spec.Sender inherited) {
        String where = "/" + path.replace('.', ' ') + " in " + file;

        List<Arg> args = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("arguments")) {
            Arg arg = Arg.parse(entry, problems);
            if (arg == null) continue;
            if (!args.isEmpty() && args.get(args.size() - 1).kind() == Arg.Kind.TEXT) {
                problems.add("argument '" + arg.name() + "' comes after a text argument, which always has to be last");
                continue;
            }
            if (!arg.optional() && !args.isEmpty() && args.get(args.size() - 1).optional()) {
                problems.add("argument '" + arg.name() + "' is required but comes after an optional one");
                continue;
            }
            args.add(arg);
        }
        for (String problem : problems) plugin.getLogger().warning(where + ": " + problem + ".");

        // Subcommands inherit their parent's sender restriction unless they define their own.
        String written = section.getString("sender");
        Spec.Sender sender = written == null ? inherited : switch (written.toLowerCase(Locale.ROOT)) {
            case "player", "players" -> Spec.Sender.PLAYER;
            case "console" -> Spec.Sender.CONSOLE;
            default -> Spec.Sender.ANY;
        };

        Map<String, Spec> subcommands = new LinkedHashMap<>();
        ConfigurationSection children = section.getConfigurationSection("subcommands");
        if (children != null) {
            for (String key : children.getKeys(false)) {
                ConfigurationSection child = children.getConfigurationSection(key);
                String word = key.toLowerCase(Locale.ROOT);
                if (child != null && child.getBoolean("enabled", true)) subcommands.put(word, parse(path + "." + word, word, child, file, sender));
            }
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection custom = section.getConfigurationSection("messages");
        if (custom != null) {
            for (String key : custom.getKeys(false)) messages.put(key.toLowerCase(Locale.ROOT), custom.getString(key, ""));
        }

        return new Spec(path, name, section.getStringList("aliases"), section.getString("description", ""),
                section.getString("permission"), sender, Durations.millis(section.getString("cooldown"), 0L),
                limit(section, where), section.getString("usage"), Map.copyOf(messages), List.copyOf(args),
                steps(section.get("actions")), Map.copyOf(subcommands));
    }

    // Supports limit: { amount: 3, reset: day } or limit: 1 for a limit that never resets.
    private @Nullable Spec.Limit limit(final ConfigurationSection section, final String where) {
        if (!section.contains("limit")) return null;
        if (section.isInt("limit")) return new Spec.Limit(Math.max(1, section.getInt("limit")), 0);

        ConfigurationSection limit = section.getConfigurationSection("limit");
        if (limit == null || limit.getInt("amount", 0) < 1) {
            plugin.getLogger().warning(where + ": limit needs an amount of at least 1.");
            return null;
        }
        String reset = limit.getString("reset", "never").toLowerCase(Locale.ROOT);
        long window = switch (reset) {
            case "never" -> 0L;
            case "hour" -> 3_600_000L;
            case "day" -> 86_400_000L;
            case "week" -> 604_800_000L;
            case "month" -> 2_592_000_000L;
            default -> Durations.millis(reset, -1L);
        };
        if (window < 0) {
            plugin.getLogger().warning(where + ": limit reset should be never, hour, day, week, month, or a time like 12h.");
            return null;
        }
        return new Spec.Limit(limit.getInt("amount"), window);
    }

    private List<Step> steps(final @Nullable Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) return List.of(new Step.Say(raw.toString()));

        List<Step> steps = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                steps.add(new Step.Say(String.valueOf(entry)));
                continue;
            }
            Step step = step(map);
            if (step != null) steps.add(step);
            else plugin.getLogger().warning("Skipped an action Lightning doesn't know: " + map);
        }
        return List.copyOf(steps);
    }

    private List<Step> stepsOf(final Map<?, ?> map) {
        Step step = step(map);
        return step == null ? List.of() : List.of(step);
    }

    private @Nullable Step step(final Map<?, ?> map) {
        if (map.containsKey("message")) return new Step.Say(String.valueOf(map.get("message")));
        if (map.containsKey("console")) return new Step.Console(String.valueOf(map.get("console")));
        if (map.containsKey("player")) return new Step.Perform(String.valueOf(map.get("player")));
        if (map.containsKey("broadcast")) {
            Object permission = map.get("permission");
            return new Step.Broadcast(String.valueOf(map.get("broadcast")), permission == null ? null : permission.toString());
        }
        if (map.containsKey("wait")) return new Step.Wait(Durations.millis(String.valueOf(map.get("wait")), 0L));
        if (map.containsKey("if")) return new Step.Branch(String.valueOf(map.get("if")), steps(map.get("then")), steps(map.get("else")));
        if (map.containsKey("chance")) {
            String percent = String.valueOf(map.get("chance")).replace("%", "").trim();
            try {
                return new Step.Chance(Double.parseDouble(percent), steps(map.get("then")), steps(map.get("else")));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (map.containsKey("stop")) return new Step.Stop();
        if (map.get("pick") instanceof List<?> options && !options.isEmpty()) {
            List<List<Step>> picks = new ArrayList<>(options.size());
            for (Object option : options) picks.add(option instanceof Map<?, ?> single ? stepsOf(single) : steps(option));
            return new Step.Pick(List.copyOf(picks));
        }
        return null;
    }
}