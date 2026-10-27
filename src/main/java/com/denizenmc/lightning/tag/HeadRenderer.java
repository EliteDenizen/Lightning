package com.denizenmc.lightning.tag;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.entity.Player;

// Only touched when the server knows about object components, so older versions never load it.
final class HeadRenderer {

    private HeadRenderer() {
    }

    // Removes the default text shadow for a cleaner head render unless shadows are enabled.
    static Component shade(final Component face, final boolean shadow) {
        return shadow ? face : face.shadowColor(ShadowColor.none());
    }

    static Component of(final Player player, final boolean hat) {
        PlayerHeadObjectContents.Builder head = ObjectContents.playerHead().id(player.getUniqueId()).name(player.getName()).hat(hat);
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            head.profileProperty(PlayerHeadObjectContents.property(property.getName(), property.getValue(), property.getSignature()));
        }
        return Component.object(head.build());
    }

    static boolean isObject(final Component component) {
        return component instanceof ObjectComponent;
    }

    static Component named(final String name, final boolean hat) {
        return Component.object(ObjectContents.playerHead().name(name).hat(hat).build());
    }
}