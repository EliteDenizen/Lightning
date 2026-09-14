package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandAction implements Action {

    private final List<String> playerCommands;
    private final List<String> consoleCommands;
    private final boolean op;
    private final boolean enabled;
    private final Condition condition;

    private CommandAction(Builder b) {
        this.playerCommands = Collections.unmodifiableList(new ArrayList<>(b.playerCommands));
        this.consoleCommands = Collections.unmodifiableList(new ArrayList<>(b.consoleCommands));
        this.op = b.op;
        this.enabled = b.enabled && (!playerCommands.isEmpty() || !consoleCommands.isEmpty());
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;

        for (String raw : consoleCommands) {
            String cmd = stripSlash(context.applyPlaceholders(raw));
            SchedulerBridge.runSync(context.getPlugin(), () -> runConsoleCommand(cmd));
        }

        Player player = context.getPlayer();
        if (player == null || !player.isOnline() || playerCommands.isEmpty()) return;

        SchedulerBridge.runForPlayer(context.getPlugin(), player, () -> runPlayerCommands(player, context));
    }

    private void runConsoleCommand(String cmd) {
        try {
            boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (!handled) {
                PluginLogger.warn("The console tried to run /{0} but no command with that name exists, "
                        + "double check the spelling in your config", cmd);
            }
        } catch (Exception e) {
            PluginLogger.error("The console couldn't run /{0}, here's why", e, cmd);
        }
    }

    private void runPlayerCommands(Player player, ActionContext context) {
        boolean wasOp = player.isOp();
        if (op && !wasOp) player.setOp(true);

        try {
            for (String raw : playerCommands) {
                String cmd = stripSlash(context.applyPlaceholders(raw));
                try {
                    boolean handled = player.performCommand(cmd);
                    if (!handled) {
                        PluginLogger.warn("Tried to make {0} run /{1} but no command with that name exists, "
                                + "double check the spelling in your config", player.getName(), cmd);
                    }
                } catch (Exception e) {
                    PluginLogger.error("{0} couldn't run /{1}, here's why", e, player.getName(), cmd);
                }
            }
        } finally {
            if (op && !wasOp) player.setOp(false);
        }
    }

    private static String stripSlash(String cmd) {
        String trimmed = cmd.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "command"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<String> playerCommands = new ArrayList<>();
        private final List<String> consoleCommands = new ArrayList<>();
        private boolean op = false;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder addPlayerCommand(String cmd) {
            if (cmd != null && !cmd.trim().isEmpty()) playerCommands.add(cmd.trim());
            return this;
        }

        public Builder addConsoleCommand(String cmd) {
            if (cmd != null && !cmd.trim().isEmpty()) consoleCommands.add(cmd.trim());
            return this;
        }

        public Builder asOp(boolean op) { this.op = op; return this; }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public CommandAction build() { return new CommandAction(this); }
    }
}
