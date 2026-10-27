package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Surface;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(@NotNull final AsyncChatEvent event) {
        if (trusted(event.getPlayer())) return;
        plugin.guard().remember(PLAIN.serialize(event.originalMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(@NotNull final PlayerCommandPreprocessEvent event) {
        if (trusted(event.getPlayer())) return;
        String command = event.getMessage();
        int first = command.indexOf(' ');
        if (first < 0) return;
        String label = command.substring(1, first).toLowerCase(Locale.ROOT);
        if (OWN_LABELS.contains(label.substring(label.indexOf(':') + 1))) return;
        plugin.guard().remember(command.substring(first + 1));
        int second = command.indexOf(' ', first + 1);
        if (second > 0) plugin.guard().remember(command.substring(second + 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(@NotNull final SignChangeEvent event) {
        if (trusted(event.getPlayer())) return;
        for (Component line : event.lines()) plugin.guard().remember(PLAIN.serialize(line));
    }

    // Trusted staff can use Lightning tags freely; their input is intended to be processed.
    private static boolean trusted(final Player player) {
        return player.hasPermission("lightning.trusted");
    }

    // Clear cached locale-dependent data when a player changes language.
    @EventHandler
    public void onLocale(@NotNull final PlayerLocaleChangeEvent event) {
        plugin.charger().forget();
    }

    @EventHandler
    public void onQuit(@NotNull final PlayerQuitEvent event) {
        plugin.faces().forget(event.getPlayer().getUniqueId());
        plugin.inspector().forget(event.getPlayer().getUniqueId());
        plugin.pulse().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(@NotNull final ServerListPingEvent event) {
        if (!plugin.settings().reaches(Surface.MOTD)) return;
        Charged charged = plugin.charger().charge(event.motd(), null, Surface.MOTD);
        if (charged != null) event.motd(charged.component());
    }
}