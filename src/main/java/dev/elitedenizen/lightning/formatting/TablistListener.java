package dev.elitedenizen.lightning.formatting;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TablistListener extends PacketListenerAbstract {

    private final Lightning plugin;

    public TablistListener(Lightning plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) return;
        if (!plugin.getPluginConfig().isConfigHelperEnabled()) return;

        WrapperPlayServerPlayerListHeaderAndFooter wrapper = new WrapperPlayServerPlayerListHeaderAndFooter(event);
        Player player = Bukkit.getPlayer(event.getUser().getUUID());
        String prefix = plugin.getPluginConfig().getConfigHelperPrefix();

        try {
            wrapper.setHeader(reformatIfPrefixed(wrapper.getHeader(), player, prefix));
            wrapper.setFooter(reformatIfPrefixed(wrapper.getFooter(), player, prefix));
            wrapper.write();
        } catch (Exception e) {
            PluginLogger.debug("Couldn't reformat a tab list update, leaving it as is: {0}", e.getMessage());
        }
    }

    private Component reformatIfPrefixed(Component original, Player player, String prefix) {
        if (original == null) return Component.empty();
        if (prefix == null || prefix.isEmpty()) return original;

        String plain = PlainTextComponentSerializer.plainText().serialize(original);
        if (!plain.startsWith(prefix)) return original;

        String withoutPrefix = plain.substring(prefix.length());
        String withPlaceholders = applyPlaceholders(player, withoutPrefix);
        return ColorFormatter.parse(withPlaceholders, player);
    }

    private String applyPlaceholders(Player player, String text) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }
}
