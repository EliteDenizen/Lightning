package dev.elitedenizen.lightning.formatting;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public final class MessageInterceptor extends PacketListenerAbstract {

    private static final Pattern ACTIONBAR_TAG = Pattern.compile("(?i)\\s*\\[actionbar]\\s*");
    private static final Pattern TITLE_TAG = Pattern.compile("(?i)\\s*\\[title]\\s*");

    private final Lightning plugin;

    public MessageInterceptor(Lightning plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) return;
        if (!plugin.getPluginConfig().isConfigHelperEnabled()) return;

        WrapperPlayServerSystemChatMessage wrapper = new WrapperPlayServerSystemChatMessage(event);
        Component original = wrapper.getMessage();
        if (original == null) return;

        String plain = PlainTextComponentSerializer.plainText().serialize(original);
        String prefix = plugin.getPluginConfig().getConfigHelperPrefix();
        if (prefix == null || prefix.isEmpty() || !plain.startsWith(prefix)) return;

        String withoutPrefix = plain.substring(prefix.length());
        UUID playerId = event.getUser().getUUID();
        String withPlaceholders = applyPlaceholders(playerId, withoutPrefix);

        boolean toActionBar = ACTIONBAR_TAG.matcher(withPlaceholders).find();
        boolean toTitle = !toActionBar && TITLE_TAG.matcher(withPlaceholders).find();

        try {
            if (toActionBar) {
                redirect(event, playerId, ACTIONBAR_TAG.matcher(withPlaceholders).replaceAll(""),
                        (player, component) -> player.sendActionBar(component));
                return;
            }

            if (toTitle) {
                redirect(event, playerId, TITLE_TAG.matcher(withPlaceholders).replaceAll(""),
                        (player, component) -> player.showTitle(Title.title(component, Component.empty())));
                return;
            }

            Component formatted = ColorFormatter.parse(withPlaceholders, Bukkit.getPlayer(playerId));
            wrapper.setMessage(formatted);
            wrapper.write();
        } catch (Exception e) {
            PluginLogger.debug("Couldn't reformat an outgoing message, leaving it as is: {0}", e.getMessage());
        }
    }

    private void redirect(PacketSendEvent event, UUID playerId, String remainingText, Delivery delivery) {
        event.setCancelled(true);
        Component formatted = ColorFormatter.parse(remainingText.trim(), Bukkit.getPlayer(playerId));
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                delivery.send(player, formatted);
            }
        });
    }

    private String applyPlaceholders(UUID playerId, String text) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }

    @FunctionalInterface
    private interface Delivery {
        void send(Player player, Component component);
    }
}
