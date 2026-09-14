package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.resolvers.ParticleResolver;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ParticleAction implements Action {

    private final Particle particle;
    private final String rawType;
    private final int count;
    private final double offsetX, offsetY, offsetZ;
    private final double speed;
    private final Object particleData;
    private final boolean enabled;
    private final Condition condition;

    private ParticleAction(Builder b) {
        this.rawType = b.type != null ? b.type.trim() : "";
        this.offsetX = b.offsetX;
        this.offsetY = b.offsetY;
        this.offsetZ = b.offsetZ;
        this.count = Math.max(1, b.count);
        this.speed = b.speed;
        this.condition = b.condition != null ? b.condition : Condition.none();

        Particle resolved = rawType.isEmpty() ? null : ParticleResolver.resolve(rawType);
        if (resolved == null && !rawType.isEmpty()) {
            PluginLogger.warn("Couldn't find a particle called '{0}'. Double check the spelling, "
                    + "or it might have been renamed in this Minecraft version.", rawType);
        }
        this.particle = resolved;
        this.particleData = resolved != null ? buildData(resolved, b) : null;
        this.enabled = b.enabled && resolved != null;
    }

    private static Object buildData(Particle particle, Builder b) {
        String name = particle.name();

        if (name.equals("DUST") && b.color != null) {
            Color color = parseColor(b.color);
            if (color != null) {
                return new Particle.DustOptions(color, (float) Math.max(0.1, b.size));
            }
        }

        if ((name.equals("ITEM") || name.equals("ITEM_CRACK")) && b.material != null) {
            Material material = resolveMaterial(b.material);
            if (material != null) return new ItemStack(material);
        }

        if ((name.equals("BLOCK") || name.equals("BLOCK_CRACK") || name.equals("BLOCK_DUST")) && b.material != null) {
            Material material = resolveMaterial(b.material);
            if (material != null && material.isBlock()) return material.createBlockData();
        }

        return null;
    }

    private static Color parseColor(String hex) {
        try {
            String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
            int rgb = Integer.parseInt(cleaned, 16);
            return Color.fromRGB(rgb);
        } catch (Exception e) {
            PluginLogger.warn("'{0}' isn't a valid color, use a hex code like #FF0000", hex);
            return null;
        }
    }

    private static Material resolveMaterial(String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            PluginLogger.warn("'{0}' isn't a material Lightning recognizes", name);
            return null;
        }
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        Location loc = player.getLocation().add(0, 1, 0);
        try {
            if (particleData != null) {
                player.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed, particleData);
            } else {
                player.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Throwable e) {
            PluginLogger.warn("The particle '{0}' didn't spawn, the server said: {1}. "
                    + "If it needs a color or material, make sure that's set in the config.",
                    rawType, e.getMessage());
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "particle"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String type;
        private int count = 10;
        private double offsetX = 0.5, offsetY = 0.5, offsetZ = 0.5;
        private double speed = 0.0;
        private String color;
        private double size = 1.0;
        private String material;
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder type(String t) { this.type = t; return this; }
        public Builder count(int c) { this.count = c; return this; }
        public Builder offsetX(double v) { this.offsetX = v; return this; }
        public Builder offsetY(double v) { this.offsetY = v; return this; }
        public Builder offsetZ(double v) { this.offsetZ = v; return this; }
        public Builder speed(double v) { this.speed = v; return this; }
        public Builder color(String c) { this.color = c; return this; }
        public Builder size(double s) { this.size = s; return this; }
        public Builder material(String m) { this.material = m; return this; }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public ParticleAction build() { return new ParticleAction(this); }
    }
}
