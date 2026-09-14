package dev.elitedenizen.lightning.util.text;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MiniPlaceholdersBridge {

    private MiniPlaceholdersBridge() {}

    public static boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders");
    }

    public static TagResolver resolverFor(Player player) {
        if (!isPresent()) return TagResolver.empty();
        try {
            if (player == null) return MiniPlaceholders.globalPlaceholders();
            return TagResolver.resolver(MiniPlaceholders.globalPlaceholders(), MiniPlaceholders.getAudiencePlaceholders((Audience) player));
        } catch (Throwable e) {
            return TagResolver.empty();
        }
    }
}
