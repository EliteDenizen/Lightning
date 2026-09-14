package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.resolvers.SoundResolver;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundAction implements Action {

    private final Sound sound;
    private final String rawName;
    private final float volume;
    private final float pitch;
    private final boolean enabled;
    private final Condition condition;

    private SoundAction(Builder b) {
        this.rawName = b.name != null ? b.name.trim() : "";
        this.sound = rawName.isEmpty() ? null : SoundResolver.resolve(rawName);
        this.volume = b.volume;
        this.pitch = b.pitch;
        this.enabled = b.enabled && sound != null;
        this.condition = b.condition != null ? b.condition : Condition.none();
        if (sound == null && !rawName.isEmpty()) {
            PluginLogger.warn("'{0}' isn't a sound Lightning recognizes", rawName);
        }
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable e) {
            PluginLogger.warn("Couldn't play the sound '{0}', the server said: {1}", rawName, e.getMessage());
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "sound"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private float volume = 1.0f;
        private float pitch = 1.0f;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder name(String n) { this.name = n;      return this; }
        public Builder volume(float v) { this.volume = v;    return this; }
        public Builder pitch(float p) { this.pitch = p;     return this; }
        public Builder enabled(boolean e) { this.enabled = e;   return this; }
        public Builder condition(Condition c){ this.condition = c; return this; }
        public SoundAction build() { return new SoundAction(this); }
    }
}
