package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.config.ConfigReader;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.entity.Player;

public final class XpAction implements Action {

    public enum Mode { POINTS, LEVELS, SET_POINTS, SET_LEVELS }

    private final int amount;
    private final Mode mode;
    private final boolean enabled;
    private final Condition condition;

    private XpAction(Builder b) {
        this.amount = b.amount;
        this.mode = b.mode;
        this.enabled = b.enabled;
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;
        switch (mode) {
            case POINTS: player.giveExp(amount); break;
            case LEVELS: player.giveExpLevels(amount); break;
            case SET_POINTS: player.setTotalExperience(Math.max(0, amount)); break;
            case SET_LEVELS: player.setLevel(Math.max(0, amount)); break;
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "xp"; }

    public static XpAction parse(Object raw) {
        Builder b = new Builder();
        if (raw instanceof Number) {
            b.amount(((Number) raw).intValue()).mode(Mode.POINTS);
            return b.build();
        }
        Object amount = ConfigReader.get(raw, "amount");
        if (amount != null) b.amount(ConfigReader.asInt(amount));
        Object modeRaw = ConfigReader.get(raw, "mode");
        if (modeRaw != null) {
            String m = modeRaw.toString().trim().toUpperCase().replace('-', '_').replace(' ', '_');
            try { b.mode(Mode.valueOf(m)); } catch (IllegalArgumentException e) {
                PluginLogger.warn("'{0}' isn't an XP mode Lightning recognizes", modeRaw);
            }
        }
        return b.build();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int amount = 0;
        private Mode mode = Mode.POINTS;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder amount(int a) { this.amount = a; return this; }
        public Builder mode(Mode m) { this.mode = m; return this; }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public XpAction build() { return new XpAction(this); }
    }
}
