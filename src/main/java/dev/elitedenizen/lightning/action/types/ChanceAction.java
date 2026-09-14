package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.action.ActionExecutor;
import dev.elitedenizen.lightning.condition.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ChanceAction implements Action {

    private final double chance;
    private final List<Action> actions;
    private final boolean enabled;
    private final Condition condition;

    private ChanceAction(Builder b) {
        this.chance = Math.max(0.0, Math.min(100.0, b.chance));
        this.actions = Collections.unmodifiableList(new ArrayList<>(b.actions));
        this.enabled = b.enabled && !actions.isEmpty() && chance > 0;
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;

        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= chance) return;

        ActionExecutor.builder()
                .addAll(actions)
                .run(context);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "chance"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private double chance = 50.0;
        private final List<Action> actions = new ArrayList<>();
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder chance(double c) { this.chance = c;      return this; }
        public Builder add(Action a) { if (a != null) actions.add(a); return this; }
        public Builder addAll(List<Action> l) { if (l != null) actions.addAll(l); return this; }
        public Builder enabled(boolean e) { this.enabled = e;     return this; }
        public Builder condition(Condition c) { this.condition = c;   return this; }
        public ChanceAction build() { return new ChanceAction(this); }
    }
}
