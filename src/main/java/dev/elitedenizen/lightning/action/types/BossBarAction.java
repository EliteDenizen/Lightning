package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class BossBarAction implements Action {

    private final String title;
    private final BarColor color;
    private final BarStyle style;
    private final float progress;
    private final long durationTicks;
    private final boolean enabled;
    private final Condition condition;

    private BossBarAction(Builder b) {
        this.title = b.title != null ? b.title : "";
        this.color = b.color;
        this.style = b.style;
        this.progress = Math.max(0f, Math.min(1f, b.progress));
        this.durationTicks = b.durationTicks;
        this.enabled = b.enabled && !this.title.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        String resolved = context.colorize(title);
        BossBar bar = Bukkit.createBossBar(resolved, color, style);
        bar.setProgress(progress);
        bar.addPlayer(player);

        if (durationTicks > 0) {
            SchedulerBridge.runLater(context.getPlugin(), () -> {
                bar.removePlayer(player);
                bar.setVisible(false);
            }, durationTicks);
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "bossbar"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title;
        private BarColor color = BarColor.YELLOW;
        private BarStyle style = BarStyle.SOLID;
        private float progress = 1.0f;
        private long durationTicks = 100L;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder title(String t) { this.title = t;      return this; }
        public Builder progress(float p) { this.progress = p;   return this; }
        public Builder enabled(boolean e) { this.enabled = e;    return this; }
        public Builder condition(Condition c) { this.condition = c;  return this; }

        public Builder duration(Object value) {
            this.durationTicks = TickConverter.toTicks(value);
            return this;
        }

        public Builder color(String raw) {
            if (raw == null) return this;
            try {
                this.color = BarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                PluginLogger.warn("'{0}' isn't a boss bar color Lightning recognizes", raw);
            }
            return this;
        }

        public Builder style(String raw) {
            if (raw == null) return this;
            try {
                this.style = BarStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                PluginLogger.warn("'{0}' isn't a boss bar style Lightning recognizes", raw);
            }
            return this;
        }

        public BossBarAction build() { return new BossBarAction(this); }
    }
}
