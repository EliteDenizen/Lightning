package dev.elitedenizen.lightning.action;

import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActionSet {

    private final List<Action> actions;
    private final Condition condition;
    private final long groupDelayTicks;
    private final ActionExecutor executor;

    private static final ActionSet EMPTY = new ActionSet(Collections.emptyList(), Condition.none(), 0);

    private ActionSet(List<Action> actions, Condition condition, long groupDelayTicks) {
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        this.condition = condition != null ? condition : Condition.none();
        this.groupDelayTicks = Math.max(0, groupDelayTicks);
        this.executor = this.actions.isEmpty() ? null : buildExecutor();
    }

    private ActionExecutor buildExecutor() {
        return ActionExecutor.builder()
                .addAll(actions)
                .groupDelayTicks(groupDelayTicks)
                .build();
    }

    public void execute(ActionContext context) {
        if (context == null || actions.isEmpty() || executor == null) return;

        if (!condition.passes(context)) {
            PluginLogger.debug("Conditions blocked /{0} from running any actions",
                    context.getCommandLabel());
            sendFailMessage(context);
            return;
        }

        PluginLogger.debug("Conditions passed for /{0}, running {1} action(s)",
                context.getCommandLabel(), actions.size());
        executor.execute(context);
    }

    private void sendFailMessage(ActionContext context) {
        String message = condition.getFailMessage();
        if (message == null || message.isEmpty()) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;
        player.sendMessage(context.colorize(message));
    }

    public List<Action> getActions() { return actions; }
    public Condition getCondition() { return condition; }
    public long getGroupDelayTicks() { return groupDelayTicks; }
    public boolean isEmpty() { return actions.isEmpty(); }

    public boolean hasAnyEnabled() {
        for (Action a : actions) {
            if (a != null && a.isEnabled()) return true;
        }
        return false;
    }

    public static ActionSet empty() { return EMPTY; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<Action> actions = new ArrayList<>(4);
        private Condition condition = Condition.none();
        private long groupDelayTicks = 0;

        private Builder() {}

        public Builder add(Action a) {
            if (a != null) actions.add(a);
            return this;
        }

        public Builder addAll(List<Action> list) {
            if (list != null) {
                for (Action a : list) {
                    if (a != null) actions.add(a);
                }
            }
            return this;
        }

        public Builder condition(Condition c) {
            if (c != null) this.condition = c;
            return this;
        }

        public Builder groupDelay(Object value) {
            this.groupDelayTicks = TickConverter.toTicks(value);
            return this;
        }

        public Builder groupDelayTicks(long ticks) {
            this.groupDelayTicks = Math.max(0, ticks);
            return this;
        }

        public ActionSet build() {
            return new ActionSet(actions, condition, groupDelayTicks);
        }
    }
}
