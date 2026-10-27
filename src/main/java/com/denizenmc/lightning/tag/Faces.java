package com.denizenmc.lightning.tag;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.ChargeState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Renders player heads with <face> for the viewer or <face:Name> for another player. Add :flat to hide the hat layer.
public final class Faces {

    public static final boolean SUPPORTED = available();

    private final Lightning plugin;
    private final Map<String, Component> heads = new ConcurrentHashMap<>();

    public Faces(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public @NotNull TagResolver resolver() {
        return TagResolver.resolver("face", this::face);
    }

    public void forget(@NotNull final UUID id) {
        heads.remove(id + ":hat");
        heads.remove(id + ":flat");
    }

    public void clear() {
        heads.clear();
    }

    public static boolean isObject(@NotNull final Component component) {
        return SUPPORTED && HeadRenderer.isObject(component);
    }

    private Tag face(final ArgumentQueue args, final Context context) {
        if (!SUPPORTED) return Tag.selfClosingInserting(MiniMessage.miniMessage().deserialize(plugin.settings().faceFallback()));

        String name = null;
        boolean hat = true;
        while (args.hasNext()) {
            String value = args.pop().value();
            if (value.equalsIgnoreCase("flat")) hat = false;
            else name = value;
        }

        Player player;
        if (name == null) {
            player = context.target() instanceof Player viewer ? viewer : null;
            ChargeState state = ChargeState.current();
            if (state != null) state.markPersonal();
        } else {
            player = Bukkit.getPlayerExact(name);
        }

        if (player != null) {
            boolean withHat = hat;
            return Tag.selfClosingInserting(heads.computeIfAbsent(player.getUniqueId() + (hat ? ":hat" : ":flat"),
                    key -> HeadRenderer.shade(HeadRenderer.of(player, withHat), plugin.settings().faceShadow())));
        }
        // Use the configured fallback when no viewer is available, such as on a kick screen during login.
        if (name == null) return Tag.selfClosingInserting(MiniMessage.miniMessage().deserialize(plugin.settings().faceFallback()));
        return Tag.selfClosingInserting(HeadRenderer.shade(HeadRenderer.named(name, hat), plugin.settings().faceShadow()));
    }

    private static boolean available() {
        try {
            Class.forName("net.kyori.adventure.text.object.ObjectContents");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}