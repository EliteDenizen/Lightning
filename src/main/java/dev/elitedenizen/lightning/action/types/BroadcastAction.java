package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BroadcastAction implements Action {

    private final String text;
    private final String permission;
    private final boolean enabled;
    private final Condition condition;

    private BroadcastAction(Builder b) {
        this.text = b.text != null ? b.text : "";
        this.permission = b.permission != null ? b.permission.trim() : "";
        this.enabled = b.enabled && !this.text.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;

        String resolved = context.colorize(text);

        if (permission.isEmpty()) {
            Bukkit.broadcastMessage(resolved);
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(permission)) {
                    online.sendMessage(resolved);
                }
            }
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "broadcast"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String text;
        private String permission;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder text(String t) { this.text = t;        return this; }
        public Builder permission(String p) { this.permission = p;  return this; }
        public Builder enabled(boolean e) { this.enabled = e;     return this; }
        public Builder condition(Condition c) { this.condition = c;   return this; }
        public BroadcastAction build() { return new BroadcastAction(this); }
    }
}
