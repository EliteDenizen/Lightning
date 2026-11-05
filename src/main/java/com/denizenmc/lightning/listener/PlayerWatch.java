package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Charger;
import com.denizenmc.lightning.charge.Surface;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

// Tracks player input for Guard and handles the MOTD, which isn't sent through a play packet.
public final class PlayerWatch implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Set<String> OWN_LABELS = Set.of("lightning", "lt");

    private final Lightning plugin;

    public PlayerWatch(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull final AsyncChatEvent event) {
        if (trusted(event.getPlayer())) return;
        plugin.guard().remember(PLAIN.serialize(event.originalMessage()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(@NotNull final PlayerCommandPreprocessEvent event) {
        if (trusted(event.getPlayer())) return;
        String command = event.getMessage();
        int first = command.indexOf(' ');
        if (first < 0) return;
        String label = command.substring(1, first).toLowerCase(Locale.ROOT);
        // Admins test tags with /lightning parse, so their input there is meant to be processed. Anyone else
        // only gets the unknown-command reply, which echoes the input back and must stay literal.
        if (OWN_LABELS.contains(label.substring(label.indexOf(':') + 1)) && event.getPlayer().hasPermission("lightning.admin")) return;
        plugin.guard().remember(command.substring(first + 1));
        int second = command.indexOf(' ', first + 1);
        if (second > 0) plugin.guard().remember(command.substring(second + 1));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSign(@NotNull final SignChangeEvent event) {
        if (trusted(event.getPlayer())) return;
        for (Component line : event.lines()) plugin.guard().remember(PLAIN.serialize(line));
    }

    // Anvil names end up on items and, through name tags, on mobs, both of which Lightning can process.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRename(@NotNull final PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || trusted(player)) return;
        plugin.guard().remember(event.getView().getRenameText());
    }

    // Items aren't processed in creative mode, but the client already holds the processed copies it was sent
    // in survival. Creative clients send items back to the server as they are, which would save Lightning's
    // formatting into the real item, so the inventory is sent again unprocessed.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(@NotNull final PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.CREATIVE || !plugin.settings().reaches(Surface.ITEMS)) return;
        Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> player.updateInventory(), null);
    }

    // Trusted staff can use Lightning tags freely; their input is intended to be processed.
    private static boolean trusted(final Player player) {
        return player.hasPermission("lightning.trusted");
    }

    // Clear the player's cached locale-dependent text when they change language. Every client reports its
    // language while joining, so clearing every player's cache here would empty it on each join.
    @EventHandler
    public void onLocale(@NotNull final PlayerLocaleChangeEvent event) {
        plugin.charger().forget(event.getPlayer().getUniqueId());
    }

    // Charging the kick reason here, rather than only in the packet, means the server logs the finished
    // message too. The result is marked so the packet listener does not charge it a second time.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKick(@NotNull final PlayerKickEvent event) {
        if (!plugin.settings().reaches(Surface.KICKS)) return;
        Charged charged = plugin.charger().charge(event.reason(), event.getPlayer(), Surface.KICKS);
        if (charged != null) event.reason(Charger.own(charged.component()));
    }

    @EventHandler
    public void onQuit(@NotNull final PlayerQuitEvent event) {
        plugin.faces().forget(event.getPlayer().getUniqueId());
        plugin.inspector().forget(event.getPlayer().getUniqueId());
        plugin.pulse().forget(event.getPlayer().getUniqueId());
        plugin.charger().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(@NotNull final ServerListPingEvent event) {
        if (!plugin.settings().reaches(Surface.MOTD)) return;
        Charged charged = plugin.charger().charge(event.motd(), null, Surface.MOTD);
        if (charged != null) event.motd(charged.component());
    }
}