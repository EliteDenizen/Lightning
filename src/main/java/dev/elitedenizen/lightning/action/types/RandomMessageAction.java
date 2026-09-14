package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomMessageAction implements Action {

    private final List<String> messages;
    private final boolean enabled;
    private final Condition condition;

    private RandomMessageAction(Builder b) {
        this.messages = Collections.unmodifiableList(new ArrayList<>(b.messages));
        this.enabled = b.enabled && !messages.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        player.sendMessage(context.colorize(messages.get(index)));
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "randomMessage"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<String> messages = new ArrayList<>();
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder add(String msg) { if (msg != null) messages.add(msg); return this; }
        public Builder addAll(List<?> list) {
            if (list != null) {
                for (Object o : list) {
                    if (o != null) messages.add(o.toString());
                }
            }
            return this;
        }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public RandomMessageAction build() { return new RandomMessageAction(this); }
    }
}
