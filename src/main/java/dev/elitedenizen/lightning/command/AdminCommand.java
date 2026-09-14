package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final Lightning plugin;

    public AdminCommand(Lightning plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lightning.admin")) {
            sender.sendMessage(plugin.getMessages().get("noPermission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload": handleReload(sender); break;
            case "reset": handleReset(sender, args); break;
            case "debug": handleDebug(sender, args); break;
            case "parse": handleParse(sender, args); break;
            case "message": handleMessage(sender, args); break;
            default: sender.sendMessage(plugin.getMessages().get("unknownSubcommand")); break;
        }

        return true;
    }

    private void handleParse(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &fUsage&7: &f/lightning parse <text>"));
            return;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String resolved = applyPlaceholdersIfPlayer(sender, raw);
        org.bukkit.entity.Player senderPlayer = sender instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) sender : null;

        sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix() + " &7Raw&7: &f" + raw));
        sender.sendMessage(ColorFormatter.colorize(resolved, senderPlayer));
        sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                + " &7That's what it looks like once placeholders and MiniMessage tags are applied."));
    }

    private void handleMessage(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &fUsage&7: &f/lightning message <player|all> <text>"));
            return;
        }
        String target = args[1];
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (target.equalsIgnoreCase("all")) {
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(ColorFormatter.colorize(applyPlaceholdersIfPlayer(online, raw), online));
            }
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix() + " &fSent to everyone online."));
            return;
        }

        org.bukkit.entity.Player online = Bukkit.getPlayerExact(target);
        if (online == null) {
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &c" + target + " isn't online."));
            return;
        }

        online.sendMessage(ColorFormatter.colorize(applyPlaceholdersIfPlayer(online, raw), online));
        sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix() + " &fSent to &e" + online.getName() + "&f."));
    }

    private String applyPlaceholdersIfPlayer(CommandSender sender, String text) {
        if (!(sender instanceof org.bukkit.entity.Player)) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((org.bukkit.entity.Player) sender, text);
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.fullReload();
            sender.sendMessage(plugin.getMessages().get("reloadSuccess"));
        } catch (Exception e) {
            PluginLogger.error("Reload failed", e);
            sender.sendMessage(plugin.getMessages().get("reloadFailed"));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &fUsage&7: &f/lightning reset <command> [player]"));
            return;
        }
        String cmdName = args[1].toLowerCase();
        if (args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            plugin.getUsageTracker().reset(target.getUniqueId(), cmdName);
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &fCleared usage of &e/" + cmdName + " &ffor &e" + args[2] + "&f."));
        } else {
            plugin.getUsageTracker().resetAll(cmdName);
            sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                    + " &fCleared all usage records for &e/" + cmdName + "&f."));
        }
    }

    private void handleDebug(CommandSender sender, String[] args) {
        PluginLogger.Level target;

        if (args.length < 2) {
            target = PluginLogger.getLevel() == PluginLogger.Level.DEBUG
                    ? PluginLogger.Level.WARN
                    : PluginLogger.Level.DEBUG;
        } else {
            target = PluginLogger.Level.parse(args[1]);
        }

        plugin.getConfig().set("debug", target.name());
        plugin.saveConfig();
        PluginLogger.reload(plugin);

        String state = target == PluginLogger.Level.DEBUG ? "enabled" : "disabled";
        sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix() + " &fDebug mode is now &e" + state + "&f."));
    }

    private void sendHelp(CommandSender sender) {
        String prefix = plugin.getMessages().getPrefix();
        sender.sendMessage(ColorFormatter.colorize(prefix + " &fAvailable Commands:"));
        sender.sendMessage(ColorFormatter.colorize("   &8- &f/lightning reload"));
        sender.sendMessage(ColorFormatter.colorize("   &8- &f/lightning reset <command> [player]"));
        sender.sendMessage(ColorFormatter.colorize("   &8- &f/lightning debug [level]"));
        sender.sendMessage(ColorFormatter.colorize("   &8- &f/lightning parse <text>"));
        sender.sendMessage(ColorFormatter.colorize("   &8- &f/lightning message <player|all> <text>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lightning.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("reload", "reset", "debug", "parse", "message");
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }

        if (args[0].equalsIgnoreCase("message") && args.length == 2) {
            List<String> out = new ArrayList<>();
            out.add("all");
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(online.getName());
            }
            return out;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length == 2) {
                List<String> out = new ArrayList<>();
                for (String name : plugin.getCommandRegistry().getRegisteredNames()) {
                    if (name.startsWith(args[1].toLowerCase())) out.add(name);
                }
                return out;
            }
            if (args.length == 3) {
                List<String> out = new ArrayList<>();
                for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(online.getName());
                }
                return out;
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            List<String> out = new ArrayList<>();
            for (PluginLogger.Level l : PluginLogger.Level.values()) {
                if (l.name().startsWith(args[1].toUpperCase())) out.add(l.name());
            }
            return out;
        }

        return Collections.emptyList();
    }
}
