package dev.elitedenizen.lightning.util.resolvers;

import org.bukkit.Particle;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ParticleResolver {

    private static final Map<String, String[]> ALIASES = new HashMap<>();

    static {
        ALIASES.put("VILLAGER_HAPPY", new String[] { "HAPPY_VILLAGER" });
        ALIASES.put("VILLAGER_ANGRY", new String[] { "ANGRY_VILLAGER" });
        ALIASES.put("REDSTONE", new String[] { "DUST" });
        ALIASES.put("SPELL", new String[] { "EFFECT" });
        ALIASES.put("SPELL_MOB", new String[] { "ENTITY_EFFECT" });
        ALIASES.put("SPELL_MOB_AMBIENT", new String[] { "AMBIENT_ENTITY_EFFECT" });
        ALIASES.put("SPELL_WITCH", new String[] { "WITCH" });
        ALIASES.put("SPELL_INSTANT", new String[] { "INSTANT_EFFECT" });
        ALIASES.put("TOWN_AURA", new String[] { "AMBIENT_ENTITY_EFFECT" });
        ALIASES.put("FLYING_GLYPH", new String[] { "ENCHANT" });
        ALIASES.put("SUSPENDED", new String[] { "UNDERWATER" });
        ALIASES.put("MOB_APPEARANCE", new String[] { "ELDER_GUARDIAN" });
        ALIASES.put("EXPLOSION_LARGE", new String[] { "EXPLOSION_EMITTER", "EXPLOSION" });
        ALIASES.put("EXPLOSION_HUGE", new String[] { "EXPLOSION_EMITTER" });
        ALIASES.put("FIREWORKS_SPARK", new String[] { "FIREWORK" });
        ALIASES.put("WATER_BUBBLE", new String[] { "BUBBLE" });
        ALIASES.put("WATER_SPLASH", new String[] { "SPLASH" });
        ALIASES.put("WATER_WAKE", new String[] { "FISHING" });
        ALIASES.put("SLIME", new String[] { "ITEM_SLIME" });
        ALIASES.put("CRIT_MAGIC", new String[] { "ENCHANTED_HIT" });
        ALIASES.put("SNOWBALL", new String[] { "ITEM_SNOWBALL" });
        ALIASES.put("SNOW_SHOVEL", new String[] { "ITEM_SNOWBALL" });
        ALIASES.put("BLOCK_CRACK", new String[] { "BLOCK" });
        ALIASES.put("BLOCK_DUST", new String[] { "BLOCK" });
        ALIASES.put("ITEM_CRACK", new String[] { "ITEM" });
        ALIASES.put("DRIP_LAVA", new String[] { "DRIPPING_LAVA" });
        ALIASES.put("DRIP_WATER", new String[] { "DRIPPING_WATER" });
        ALIASES.put("BARRIER", new String[] { "BLOCK_MARKER" });
    }

    private ParticleResolver() {}

    public static Particle resolve(String name) {
        if (name == null || name.trim().isEmpty()) return null;

        String cleaned = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        Particle direct = tryValueOf(cleaned);
        if (direct != null) return direct;

        String[] alternatives = ALIASES.get(cleaned);
        if (alternatives != null) {
            for (String alt : alternatives) {
                Particle found = tryValueOf(alt);
                if (found != null) return found;
            }
        }

        for (Particle p : Particle.values()) {
            if (p.name().equalsIgnoreCase(cleaned)) return p;
        }

        return null;
    }

    private static Particle tryValueOf(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
