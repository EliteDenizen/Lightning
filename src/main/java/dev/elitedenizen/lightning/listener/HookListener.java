package dev.elitedenizen.lightning.listener;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.resolver.CommandOwner;
import dev.elitedenizen.lightning.rule.Rule;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.List;

public final class HookListener implements Listener {

    private final Lightning plugin;

    public HookListener(Lightning plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;

        String typedNamespace = extractNamespace(raw.startsWith("/") ? raw.substring(1) : raw);
        String fullCommand = stripNamespace(raw.startsWith("/") ? raw.substring(1) : raw);
        String label = fullCommand.split("\\s+")[0].toLowerCase();
        String[] args = splitArgs(fullCommand);

        if (plugin.getPluginConfig().isCommandIgnored(label)) {
            PluginLogger.debug("/{0} is on the ignore list, skipping it entirely", label);
            return;
        }

        CommandOwner owner = plugin.getOwnerResolver().resolve(label, typedNamespace);
        String pluginName = owner.getPluginName();

        if (plugin.getPluginConfig().isPluginIgnored(pluginName)) {
            PluginLogger.debug("/{0} belongs to {1}, which is on the ignore list, skipping it", label, pluginName);
            return;
        }

        List<Rule> matched = plugin.getRuleBook().findMatching(pluginName, fullCommand);
        if (matched.isEmpty()) {
            PluginLogger.debug("/{0} resolved to plugin '{1}', no hook rule matched that",
                    label, pluginName.isEmpty() ? "unknown" : pluginName);
            return;
        }
        PluginLogger.debug("/{0} matched {1} hook rule(s) under '{2}', running them now",
                label, matched.size(), pluginName);

        Player player = event.getPlayer();

        for (Rule rule : matched) {
            schedule(rule, event, player, pluginName, label, fullCommand, args);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand();
        if (raw == null || raw.isEmpty()) return;

        String typedNamespace = extractNamespace(raw.startsWith("/") ? raw.substring(1) : raw);
        String fullCommand = stripNamespace(raw.startsWith("/") ? raw.substring(1) : raw);
        String label = fullCommand.split("\\s+")[0].toLowerCase();
        String[] args = splitArgs(fullCommand);

        if (plugin.getPluginConfig().isCommandIgnored(label)) {
            PluginLogger.debug("/{0} is on the ignore list, skipping it entirely", label);
            return;
        }

        CommandOwner owner = plugin.getOwnerResolver().resolve(label, typedNamespace);
        String pluginName = owner.getPluginName();

        if (plugin.getPluginConfig().isPluginIgnored(pluginName)) {
            PluginLogger.debug("/{0} belongs to {1}, which is on the ignore list, skipping it", label, pluginName);
            return;
        }

        List<Rule> matched = plugin.getRuleBook().findMatching(pluginName, fullCommand);
        if (matched.isEmpty()) {
            PluginLogger.debug("/{0} resolved to plugin '{1}', no hook rule matched that",
                    label, pluginName.isEmpty() ? "unknown" : pluginName);
            return;
        }
        PluginLogger.debug("/{0} matched {1} hook rule(s) under '{2}', running them now",
                label, matched.size(), pluginName);

        for (Rule rule : matched) {
            schedule(rule, event, null, pluginName, label, fullCommand, args);
        }
    }

    private void schedule(Rule rule, Cancellable event, Player player, String pluginName,
            String label, String fullCommand, String[] args) {
        Runnable fire = () -> {
            if (event.isCancelled() && !plugin.getPluginConfig().isRunOnCancelled()) {
                PluginLogger.debug("Skipped hook for /{0}, the command was blocked before it ran", label);
                return;
            }

            ActionContext context = ActionContext.builder()
                    .plugin(plugin)
                    .player(player)
                    .ownerPlugin(pluginName)
                    .commandLabel(label)
                    .fullCommand(fullCommand)
                    .args(args)
                    .build();

            rule.execute(context);
        };

        if (player != null) {
            SchedulerBridge.runLaterForPlayer(plugin, player, fire, 1L);
        } else {
            SchedulerBridge.runLater(plugin, fire, 1L);
        }
    }

    private static String extractNamespace(String fullCommand) {
        int spaceIndex = fullCommand.indexOf(' ');
        String label = spaceIndex == -1 ? fullCommand : fullCommand.substring(0, spaceIndex);
        int colonIndex = label.indexOf(':');
        return colonIndex == -1 ? null : label.substring(0, colonIndex).toLowerCase();
    }

    private static String stripNamespace(String fullCommand) {
        int spaceIndex = fullCommand.indexOf(' ');
        String label = spaceIndex == -1 ? fullCommand : fullCommand.substring(0, spaceIndex);
        String rest = spaceIndex == -1 ? "" : fullCommand.substring(spaceIndex);

        int colonIndex = label.indexOf(':');
        if (colonIndex == -1) return fullCommand;

        return label.substring(colonIndex + 1) + rest;
    }

    private static String[] splitArgs(String fullCommand) {
        int spaceIndex = fullCommand.indexOf(' ');
        return spaceIndex == -1 ? new String[0] : fullCommand.substring(spaceIndex + 1).split("\\s+");
    }
}
