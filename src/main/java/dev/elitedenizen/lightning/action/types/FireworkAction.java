package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FireworkAction implements Action {

    private final FireworkEffect.Type type;
    private final List<Color> colors;
    private final List<Color> fadeColors;
    private final boolean flicker;
    private final boolean trail;
    private final int power;
    private final boolean enabled;
    private final Condition condition;

    private FireworkAction(Builder b) {
        this.type = b.type;
        this.colors = b.colors.isEmpty() ? defaultColors() : new ArrayList<>(b.colors);
        this.fadeColors = new ArrayList<>(b.fadeColors);
        this.flicker = b.flicker;
        this.trail = b.trail;
        this.power = Math.max(0, Math.min(3, b.power));
        this.enabled = b.enabled;
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    private static List<Color> defaultColors() {
        List<Color> colors = new ArrayList<>();
        colors.add(Color.YELLOW);
        return colors;
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(colors)
                    .withFade(fadeColors)
                    .flicker(flicker)
                    .trail(trail)
                    .build());
            meta.setPower(power);
            firework.setFireworkMeta(meta);
        } catch (Exception e) {
            PluginLogger.warn("Couldn't spawn a firework for {0}: {1}", player.getName(), e.getMessage());
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "firework"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private FireworkEffect.Type type = FireworkEffect.Type.BALL;
        private final List<Color> colors = new ArrayList<>();
        private final List<Color> fadeColors = new ArrayList<>();
        private boolean flicker = false;
        private boolean trail = false;
        private int power = 1;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder type(String raw) {
            if (raw == null) return this;
            try {
                this.type = FireworkEffect.Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                PluginLogger.warn("'{0}' isn't a firework type Lightning recognizes", raw);
            }
            return this;
        }

        public Builder addColor(String hex) {
            Color parsed = parseColor(hex);
            if (parsed != null) colors.add(parsed);
            return this;
        }

        public Builder addFadeColor(String hex) {
            Color parsed = parseColor(hex);
            if (parsed != null) fadeColors.add(parsed);
            return this;
        }

        private Color parseColor(String hex) {
            if (hex == null) return null;
            try {
                String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
                return Color.fromRGB(Integer.parseInt(cleaned, 16));
            } catch (Exception e) {
                PluginLogger.warn("'{0}' isn't a valid firework color, use a hex code like #FFAA00", hex);
                return null;
            }
        }

        public Builder flicker(boolean f) { this.flicker = f; return this; }
        public Builder trail(boolean t) { this.trail = t; return this; }
        public Builder power(int p) { this.power = p; return this; }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public FireworkAction build() { return new FireworkAction(this); }
    }
}
