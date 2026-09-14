package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.entity.Player;

public final class TitleAction implements Action {

    private final String title;
    private final String subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;
    private final boolean enabled;
    private final Condition condition;

    private TitleAction(Builder b) {
        this.title = b.title != null ? b.title : "";
        this.subtitle = b.subtitle != null ? b.subtitle : "";
        this.fadeIn = b.fadeIn;
        this.stay = b.stay;
        this.fadeOut = b.fadeOut;
        this.enabled = b.enabled && (!this.title.isEmpty() || !this.subtitle.isEmpty());
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        String t = context.colorize(title);
        String st = context.colorize(subtitle);

        player.sendTitle(t, st, fadeIn, stay, fadeOut);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "title"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title, subtitle;
        private int fadeIn = 10, stay = 70, fadeOut = 20;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder title(String t) { this.title = t;     return this; }
        public Builder subtitle(String s) { this.subtitle = s;  return this; }
        public Builder fadeIn(int v) { this.fadeIn = v;    return this; }
        public Builder stay(int v) { this.stay = v;      return this; }
        public Builder fadeOut(int v) { this.fadeOut = v;   return this; }
        public Builder enabled(boolean e) { this.enabled = e;   return this; }
        public Builder condition(Condition c){ this.condition = c; return this; }
        public TitleAction build() { return new TitleAction(this); }
    }
}
