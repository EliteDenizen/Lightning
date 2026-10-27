package com.denizenmc.lightning.command;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Surface;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class LightningCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("reload", "parse", "sparks", "inspect", "check", "convert", "debug");
    private static final List<String> INSPECTABLE = Arrays.stream(Surface.values())
            .filter(surface -> surface != Surface.KICKS && surface != Surface.MOTD).map(Surface::key).toList();

    private final Lightning plugin;

    public LightningCommand(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull final CommandSourceStack source, @NotNull final String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission("lightning.admin")) {
            sender.sendMessage(plugin.message("denied"));
            return;
        }

        switch (args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.load();
                sender.sendMessage(plugin.message("reloaded",
                        Placeholder.unparsed("sparks", String.valueOf(plugin.sparks().names().size())),
                        Placeholder.unparsed("rewires", String.valueOf(plugin.rewires().size())),
                        Placeholder.unparsed("commands", String.valueOf(plugin.commands().size()))));
            }
            case "parse" -> parse(sender, args);
            case "sparks" -> sender.sendMessage(plugin.message("sparks",
                    Placeholder.unparsed("count", String.valueOf(plugin.sparks().names().size())),
                    Placeholder.unparsed("names", String.join(", ", new TreeSet<>(plugin.sparks().names())))));
            case "inspect" -> inspect(sender, args);
            case "debug" -> sender.sendMessage(plugin.message(plugin.settings().toggleDebug() ? "debugging" : "quiet"));
            case "check" -> plugin.doctor().check(sender, args.length > 1 ? args[1] : null);
            case "convert" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.message("usage", Placeholder.unparsed("usage", "/lightning convert <plugin> [confirm]")));
                    return;
                }
                plugin.converter().convert(sender, args[1], args.length > 2 && args[2].equalsIgnoreCase("confirm"));
            }
            default -> sender.sendMessage(plugin.message("help"));
        }
    }

    private void inspect(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("players"));
            return;
        }

        // Inspect any supported surface. Kick screens and MOTDs are handled before players enter the game.
        Surface surface = args.length > 1 ? Surface.byKey(args[1]) : Surface.CHAT;
        if (surface == null || surface == Surface.KICKS || surface == Surface.MOTD) {
            sender.sendMessage(plugin.message("usage", Placeholder.unparsed("usage", "/lightning inspect [" + String.join("|", INSPECTABLE) + "]")));
            return;
        }
        boolean on = plugin.inspector().toggle(player.getUniqueId(), surface);
        sender.sendMessage(plugin.message(on ? "inspecting" : "stopped", Placeholder.unparsed("surface", surface.key())));
    }

    // Runs text through the same processing pipeline used for real packets, including configured effects.
    private void parse(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.message("usage", Placeholder.unparsed("usage", "/lightning parse [surface] <text>")));
            return;
        }

        // An optional surface lets you test rules and rewires for a specific context, such as kick messages.
        Surface surface = Surface.byKey(args[1]);
        int from = surface == null ? 1 : 2;
        if (surface == null) surface = Surface.CHAT;
        if (from >= args.length) {
            sender.sendMessage(plugin.message("usage", Placeholder.unparsed("usage", "/lightning parse [surface] <text>")));
            return;
        }

        // Use \n to represent a line break in command input.
        String raw = String.join(" ", Arrays.copyOfRange(args, from, args.length)).replace("\\n", "\n");
        Player viewer = sender instanceof Player player ? player : null;
        Charged charged = plugin.charger().charge(Component.text(raw), viewer, surface);
        if (charged == null) {
            sender.sendMessage(plugin.message("untouched"));
            return;
        }

        if (viewer != null) charged.effects().forEach(effect -> effect.accept(viewer));
        if (charged.silent()) return;
        switch (charged.delivery()) {
            case ACTIONBAR -> sender.sendActionBar(charged.component());
            case TITLE -> sender.showTitle(Title.title(charged.component(), Component.empty()));
            case IN_PLACE -> sender.sendMessage(charged.component());
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull final CommandSourceStack source, @NotNull final String[] args) {
        if (!source.getSender().hasPermission("lightning.admin")) return List.of();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) return filter(SUBCOMMANDS, typed);

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("inspect")) return filter(INSPECTABLE, typed);
        if (args.length == 2 && sub.equals("parse")) return filter(Arrays.stream(Surface.values()).map(Surface::key).toList(), typed);
        if (args.length == 2 && (sub.equals("check") || sub.equals("convert"))) return filter(plugins(), typed);
        if (args.length == 3 && sub.equals("convert")) return filter(List.of("confirm"), typed);
        return List.of();
    }

    private List<String> plugins() {
        File[] folders = plugin.getDataFolder().getParentFile().listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (folders != null) for (File folder : folders) names.add(folder.getName());
        return names;
    }

    private static List<String> filter(final List<String> options, final String typed) {
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}