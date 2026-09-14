package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.entity.Player;

public final class ActionBarAction implements Action {

    private final String text;
    private final boolean enabled;
    private final Condition condition;

    private ActionBarAction(Builder b) {
        this.text = b.text != null ? b.text : "";
        this.enabled = b.enabled && !this.text.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        String resolved = context.colorize(text);
        player.sendActionBar(resolved);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "actionbar"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String text;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder text(String t) { this.text = t;      return this; }
        public Builder enabled(boolean e) { this.enabled = e;   return this; }
        public Builder condition(Condition c){ this.condition = c; return this; }
        public ActionBarAction build() { return new ActionBarAction(this); }
    }
}
