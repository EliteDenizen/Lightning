package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.config.ConfigReader;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PotionAction implements Action {

    private final List<PotionEffect> effects;
    private final boolean clear;
    private final boolean enabled;
    private final Condition condition;

    private PotionAction(Builder b) {
        this.effects = Collections.unmodifiableList(new ArrayList<>(b.effects));
        this.clear = b.clear;
        this.enabled = b.enabled && (!effects.isEmpty() || clear);
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (clear) {
            for (PotionEffect active : player.getActivePotionEffects()) {
                player.removePotionEffect(active.getType());
            }
        }

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect, true);
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "potion"; }

    public static PotionAction parse(Object raw) {
        Builder b = new Builder();

        if (raw instanceof List) {
            for (Object entry : (List<?>) raw) {
                parseEntry(b, entry);
            }
        } else if (raw instanceof String) {
            parseEntry(b, raw);
        } else if (raw != null) {
            Object clear = ConfigReader.get(raw, "clear");
            if (clear != null) b.clear(ConfigReader.asBool(clear));

            Object list = ConfigReader.get(raw, "effects");
            if (list instanceof List) {
                for (Object entry : (List<?>) list) parseEntry(b, entry);
            } else if (list instanceof String) {
                parseEntry(b, list);
            }
        }

        return b.build();
    }

    private static void parseEntry(Builder b, Object entry) {
        if (entry instanceof String) {
            PotionEffect effect = fromShorthand(entry.toString());
            if (effect != null) b.addEffect(effect);
            return;
        }
        Object typeRaw = ConfigReader.get(entry, "type");
        if (typeRaw == null) typeRaw = ConfigReader.get(entry, "effect");
        if (typeRaw == null) return;

        PotionEffectType pet = resolveType(typeRaw.toString());
        if (pet == null) {
            PluginLogger.warn("'{0}' isn't a potion effect Lightning recognizes", typeRaw);
            return;
        }

        Object durationRaw = ConfigReader.get(entry, "duration");
        Object ampRaw = ConfigReader.get(entry, "amplifier");
        Object ambientRaw = ConfigReader.get(entry, "ambient");
        Object particlesRaw = ConfigReader.get(entry, "particles");

        int duration = durationRaw  != null ? ConfigReader.asInt(durationRaw) * 20 : 200;
        int amplifier = ampRaw       != null ? ConfigReader.asInt(ampRaw) : 0;
        boolean ambient = ambientRaw   != null && ConfigReader.asBool(ambientRaw);
        boolean particles = particlesRaw == null || ConfigReader.asBool(particlesRaw);

        b.addEffect(new PotionEffect(pet, duration, amplifier, ambient, particles));
    }

    private static PotionEffect fromShorthand(String raw) {
        String[] parts = raw.trim().split(":");
        PotionEffectType pet = resolveType(parts[0]);
        if (pet == null) {
            PluginLogger.warn("'{0}' isn't a potion effect Lightning recognizes", parts[0]);
            return null;
        }
        int duration = parts.length > 1 ? parseNum(parts[1]) * 20 : 200;
        int amplifier = parts.length > 2 ? parseNum(parts[2]) : 0;
        return new PotionEffect(pet, duration, amplifier);
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType resolveType(String name) {
        if (name == null || name.isEmpty()) return null;
        String upper = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        PotionEffectType type = PotionEffectType.getByName(upper);
        if (type != null) return type;

        for (PotionEffectType t : PotionEffectType.values()) {
            if (t.getName().equalsIgnoreCase(upper)) return t;
            try {
                if (t.getKey().getKey().equalsIgnoreCase(upper.toLowerCase(Locale.ROOT))) return t;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static int parseNum(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<PotionEffect> effects = new ArrayList<>();
        private boolean clear = false;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder addEffect(PotionEffect e) { if (e != null) effects.add(e); return this; }
        public Builder clear(boolean c) { this.clear = c;     return this; }
        public Builder enabled(boolean e) { this.enabled = e;   return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public PotionAction build() { return new PotionAction(this); }
    }
}
