package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageAction implements Action {

    private final List<String> lines;
    private final boolean enabled;
    private final Condition condition;

    private MessageAction(Builder b) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(b.lines));
        this.enabled = b.enabled && !this.lines.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        for (String line : lines) {
            player.sendMessage(context.colorize(line));
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "message"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<String> lines = new ArrayList<>();
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder text(String t) {
            if (t != null) lines.add(t);
            return this;
        }

        public Builder lines(List<String> l) {
            if (l != null) lines.addAll(l);
            return this;
        }

        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public MessageAction build() { return new MessageAction(this); }
    }
}
