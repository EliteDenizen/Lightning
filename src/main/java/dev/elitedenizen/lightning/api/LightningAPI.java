package dev.elitedenizen.lightning.api;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.action.ActionExecutor;
import dev.elitedenizen.lightning.command.CustomCommand;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LightningAPI {

    private LightningAPI() {}

    public static boolean isAvailable() {
        return Lightning.get() != null;
    }

    public static void runTemplate(String name, Player player) {
        runTemplate(name, player, null);
    }

    public static void runTemplate(String name, Player player, Map<String, String> variables) {
        Objects.requireNonNull(name, "template name cannot be null");
        Lightning plugin = require();

        List<Action> actions = plugin.getTemplateManager().get(name);
        if (actions.isEmpty()) {
            PluginLogger.warn("LightningAPI: another plugin asked for template '{0}' but it doesn't exist", name);
            return;
        }

        ActionContext context = ActionContext.builder()
                .plugin(plugin)
                .player(player)
                .commandLabel("api")
                .variables(variables)
                .build();

        ActionExecutor.builder().addAll(actions).run(context);
    }

    public static boolean templateExists(String name) {
        Lightning plugin = Lightning.get();
        return plugin != null && name != null && plugin.getTemplateManager().has(name);
    }

    public static boolean runCommandActions(String commandName, Player player) {
        return runCommandActions(commandName, player, null);
    }

    public static boolean runCommandActions(String commandName, Player player, Map<String, String> variables) {
        Objects.requireNonNull(commandName, "command name cannot be null");
        Lightning plugin = require();

        CustomCommand command = plugin.findCommand(commandName);
        if (command == null) {
            PluginLogger.warn("LightningAPI: another plugin asked to run '{0}' but no command with that name is loaded", commandName);
            return false;
        }

        ActionContext context = ActionContext.builder()
                .plugin(plugin)
                .player(player)
                .commandLabel(command.getName())
                .variables(variables)
                .build();

        command.getActions().execute(context);
        return true;
    }

    public static CustomCommand findCommand(String commandName) {
        Lightning plugin = Lightning.get();
        return plugin != null ? plugin.findCommand(commandName) : null;
    }

    public static boolean isCommandRegistered(String commandName) {
        return findCommand(commandName) != null;
    }

    public static int getUseCount(UUID player, String commandName) {
        Objects.requireNonNull(player, "player uuid cannot be null");
        Lightning plugin = Lightning.get();
        if (plugin == null) return 0;
        return plugin.getUsageTracker().getUseCount(player, commandName);
    }

    public static void resetUses(UUID player, String commandName) {
        Objects.requireNonNull(player, "player uuid cannot be null");
        Lightning plugin = Lightning.get();
        if (plugin == null) return;
        plugin.getUsageTracker().reset(player, commandName);
    }

    public static void resetAllUses(String commandName) {
        Lightning plugin = Lightning.get();
        if (plugin == null) return;
        plugin.getUsageTracker().resetAll(commandName);
    }

    public static boolean isOnCooldown(UUID player, String commandName) {
        Lightning plugin = Lightning.get();
        if (plugin == null) return false;
        CustomCommand cmd = plugin.findCommand(commandName);
        return cmd != null && plugin.getUsageTracker().hasCooldown(player, cmd);
    }

    public static long getCooldownRemaining(UUID player, String commandName) {
        Objects.requireNonNull(player, "player uuid cannot be null");
        Lightning plugin = Lightning.get();
        if (plugin == null) return 0;
        CustomCommand cmd = plugin.findCommand(commandName);
        if (cmd == null) return 0;
        return plugin.getUsageTracker().getCooldownRemaining(player, cmd);
    }

    public static boolean hasReachedUsesLimit(UUID player, String commandName) {
        Lightning plugin = Lightning.get();
        if (plugin == null) return false;
        CustomCommand cmd = plugin.findCommand(commandName);
        return cmd != null && plugin.getUsageTracker().hasReachedUsesLimit(player, cmd);
    }

    public static ActionContext.Builder contextBuilder(Player player) {
        Lightning plugin = require();
        return ActionContext.builder().plugin(plugin).player(player).commandLabel("api");
    }

    private static Lightning require() {
        Lightning plugin = Lightning.get();
        if (plugin == null) {
            throw new IllegalStateException("Lightning isn't loaded yet, check LightningAPI.isAvailable() first");
        }
        return plugin;
    }
}
