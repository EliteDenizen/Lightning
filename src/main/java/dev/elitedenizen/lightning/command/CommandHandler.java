package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommandHandler {

    private final Lightning plugin;
    private final CustomCommand definition;
    private final UsageTracker usageTracker;

    public CommandHandler(Lightning plugin, CustomCommand definition, UsageTracker usageTracker) {
        this.plugin = plugin;
        this.definition = definition;
        this.usageTracker = usageTracker;
    }

    public void onCommand(CommandSender sender, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;

        if (definition.isPlayerOnly() && player == null) {
            sender.sendMessage(plugin.getMessages().get("playerOnly"));
            return;
        }

        if (definition.isConsoleOnly() && player != null) {
            PluginLogger.debug("{0} tried to run console-only /{1} in-game, blocked", player.getName(), label);
            sender.sendMessage(plugin.getMessages().get("consoleOnly"));
            return;
        }

        if (!definition.getPermission().isEmpty() && !sender.hasPermission(definition.getPermission())) {
            String msg = definition.getPermissionMessage();
            if (msg.isEmpty()) msg = plugin.getMessages().get("noPermission");
            sender.sendMessage(ColorFormatter.colorize(msg));
            PluginLogger.debug("{0} was denied /{1}, missing permission '{2}'",
                    sender.getName(), label, definition.getPermission());
            return;
        }

        if (player != null && definition.hasWorldRestriction() && !definition.isWorldAllowed(player.getWorld().getName())) {
            String msg = definition.getWorldDeniedMessage();
            if (msg.isEmpty()) msg = plugin.getMessages().get("wrongWorld");
            sender.sendMessage(ColorFormatter.colorize(msg));
            PluginLogger.debug("{0} tried /{1} in disallowed world '{2}'",
                    player.getName(), label, player.getWorld().getName());
            return;
        }

        if (player != null && definition.hasCooldown() && isOnCooldown(player, label)) {
            return;
        }

        if (player != null && definition.hasUsesLimit() && hasReachedUsesLimit(player, label)) {
            return;
        }

        if (isMissingRequiredArgument(sender, args)) {
            return;
        }

        if (player != null) {
            usageTracker.recordUse(player.getUniqueId(), definition);
            if (definition.hasCooldown()) {
                usageTracker.startCooldown(player.getUniqueId(), definition);
            }
        }

        runActions(sender, player, label, args);

        if (definition.shouldLogUsage() && player != null) {
            logUsage(player, label, args);
        }
    }

    private boolean isOnCooldown(Player player, String label) {
        boolean bypass = !definition.getCooldownBypassPermission().isEmpty()
                && player.hasPermission(definition.getCooldownBypassPermission());
        if (bypass) return false;

        if (!usageTracker.hasCooldown(player.getUniqueId(), definition)) return false;

        long remaining = usageTracker.getCooldownRemaining(player.getUniqueId(), definition);
        String msg = definition.getCooldownMessage();
        if (msg.isEmpty()) msg = plugin.getMessages().get("cooldown");
        String formatted = TickConverter.format(remaining);
        msg = msg.replace("{time}", formatted).replace("{remaining}", formatted);
        player.sendMessage(ColorFormatter.colorize(msg));
        return true;
    }

    private boolean hasReachedUsesLimit(Player player, String label) {
        boolean bypass = !definition.getUsesBypassPermission().isEmpty()
                && player.hasPermission(definition.getUsesBypassPermission());
        if (bypass) return false;

        if (!usageTracker.hasReachedUsesLimit(player.getUniqueId(), definition)) return false;

        String msg = definition.getUsesMessage();
        if (msg.isEmpty()) msg = plugin.getMessages().get("maxUsesReached");
        player.sendMessage(ColorFormatter.colorize(msg));
        return true;
    }

    private void runActions(CommandSender sender, Player player, String label, String[] args) {
        if (definition.getActions().isEmpty()) return;

        Map<String, String> vars = new HashMap<>();
        if (player != null) {
            vars.put("uses", String.valueOf(usageTracker.getUseCount(player.getUniqueId(), definition)));
        }

        ActionContext context = ActionContext.builder()
                .plugin(plugin)
                .player(player)
                .commandLabel(label)
                .fullCommand(label + (args.length > 0 ? " " + String.join(" ", args) : ""))
                .args(args)
                .variables(vars)
                .build();

        definition.getActions().execute(context);
    }

    private void logUsage(Player player, String label, String[] args) {
        SchedulerBridge.runAsync(plugin, () -> {
            try {
                File logFile = new File(plugin.getDataFolder(), "logs/usage.log");
                logFile.getParentFile().mkdirs();
                try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    String fullCmd = label + (args.length > 0 ? " " + String.join(" ", args) : "");
                    writer.println("[" + timestamp + "] " + player.getName()
                            + " (" + player.getUniqueId() + ") ran /" + fullCmd);
                }
            } catch (IOException e) {
                PluginLogger.error("Could not write to logs/usage.log for /{0}", e, label);
            }
        });
    }

    private boolean isMissingRequiredArgument(CommandSender sender, String[] args) {
        List<CommandArgument> arguments = definition.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);

            if (argument.isRequired() && args.length <= i) {
                sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix()
                        + " &fUsage&7: &f" + definition.getUsage()));
                return true;
            }

            if (args.length > i && argument.hasPattern() && !argument.matchesPattern(args[i])) {
                String message = argument.getPatternMessage();
                if (message == null || message.isEmpty()) {
                    message = "&f'" + args[i] + "' &7isn't a valid value for &f" + argument.getName() + "&7.";
                }
                sender.sendMessage(ColorFormatter.colorize(plugin.getMessages().getPrefix() + " " + message));
                return true;
            }
        }
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, String alias, String[] args) {
        List<CommandArgument> arguments = definition.getArguments();
        int index = args.length - 1;
        if (index < 0 || index >= arguments.size()) return Collections.emptyList();

        CommandArgument argument = arguments.get(index);
        String typed = args[index].toLowerCase();
        List<String> candidates;

        switch (argument.getSuggestionType()) {
            case PLAYERS:
                candidates = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    candidates.add(online.getName());
                }
                break;
            case WORLDS:
                candidates = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    candidates.add(world.getName());
                }
                break;
            case STATIC:
                candidates = argument.getStaticSuggestions();
                break;
            default:
                return Collections.emptyList();
        }

        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(typed)) matches.add(candidate);
        }
        return matches;
    }

    public CustomCommand getDefinition() {
        return definition;
    }
}
