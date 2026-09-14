package dev.elitedenizen.lightning.action;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.action.types.DelayAction;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ActionExecutor {

    private final List<Action> actions;
    private final long groupDelayTicks;

    private ActionExecutor(List<Action> actions, long groupDelayTicks) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        this.groupDelayTicks = Math.max(0, groupDelayTicks);
    }

    public void execute(ActionContext context) {
        if (context == null || actions.isEmpty()) return;

        Player player = context.getPlayer();
        Lightning plugin = context.getPlugin();

        List<Action> runnable = new ArrayList<>(actions.size());
        for (Action a : actions) {
            if (a == null || !a.isEnabled()) continue;
            if (!a.shouldExecute(context)) {
                PluginLogger.debug("Skipping the {0} action, its conditions weren't met", a.getType());
                continue;
            }
            runnable.add(a);
        }

        if (runnable.isEmpty()) return;

        long timeline = 0;
        boolean first = true;
        boolean lastWasDelay = false;

        for (Action action : runnable) {
            if (action instanceof DelayAction) {
                long delay = Math.max(0, action.getDelayTicks());
                timeline += delay;
                lastWasDelay = true;
                continue;
            }

            if (!first && !lastWasDelay && groupDelayTicks > 0) {
                timeline += groupDelayTicks;
            }

            if (timeline <= 0 || player == null) {
                fire(action, context);
            } else {
                scheduleForPlayer(plugin, action, context, timeline);
            }

            first = false;
            lastWasDelay = false;
        }
    }

    private void fire(Action action, ActionContext context) {
        try {
            if (context.getPlayer() != null && !context.getPlayer().isOnline()) return;
            action.execute(context);
            PluginLogger.debug("Ran the {0} action for /{1}", action.getType(), context.getCommandLabel());
        } catch (Exception e) {
            PluginLogger.error("The {0} action threw an exception while running", e, action.getType());
        }
    }

    private void scheduleForPlayer(Lightning plugin, Action action, ActionContext context, long delayTicks) {
        Player player = context.getPlayer();
        if (player == null) {
            PluginLogger.debug("Skipping the delayed {0} action, there's no player to run it for", action.getType());
            return;
        }

        final String playerName = player.getName();

        SchedulerBridge.runLaterForPlayer(plugin, player, () -> {
            Player online = Bukkit.getPlayerExact(playerName);
            if (online == null || !online.isOnline()) {
                PluginLogger.debug("Skipping the delayed {0} action, the player logged off before it fired", action.getType());
                return;
            }

            ActionContext refreshed = ActionContext.builder()
                    .plugin(context.getPlugin())
                    .player(online)
                    .ownerPlugin(context.getOwnerPlugin())
                    .commandLabel(context.getCommandLabel())
                    .fullCommand(context.getFullCommand())
                    .args(context.getArgs())
                    .variables(context.getVariables())
                    .build();

            fire(action, refreshed);
        }, delayTicks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Action> actions = new ArrayList<>();
        private long groupDelayTicks = 0;

        private Builder() {}

        public Builder add(Action a) {
            if (a != null) actions.add(a);
            return this;
        }

        public Builder addAll(List<Action> list) {
            if (list != null) actions.addAll(list);
            return this;
        }

        public Builder groupDelayTicks(long ticks) {
            this.groupDelayTicks = Math.max(0, ticks);
            return this;
        }

        public ActionExecutor build() {
            return new ActionExecutor(actions, groupDelayTicks);
        }

        public void run(ActionContext context) {
            build().execute(context);
        }
    }
}
