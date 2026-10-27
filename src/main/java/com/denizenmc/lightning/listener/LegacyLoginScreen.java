package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Charger;
import com.denizenmc.lightning.charge.Surface;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

// Handles login refusals on older servers where PlayerLoginEvent determines the kick message.
@SuppressWarnings("deprecation")
public final class LegacyLoginScreen implements Listener {

    private final Lightning plugin;

    public LegacyLoginScreen(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(@NotNull final PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED || !plugin.settings().reaches(Surface.KICKS)) return;
        Charged charged = plugin.charger().charge(event.kickMessage(), null, Surface.KICKS);
        // Log the original message to help identify the text a rewire should match.
        plugin.debug("A login was refused" + (charged == null ? ", nothing matched: " : ", charged: ") + Inspector.describe(event.kickMessage()));
        if (charged != null) event.kickMessage(Charger.own(charged.component()));
    }
}