package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Charger;
import com.denizenmc.lightning.charge.Surface;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

// Handles login refusals on Paper 1.21.6 and newer, where bans, whitelist checks, and full-server
// messages can be processed before a disconnect packet is available. Older versions use LegacyLoginScreen.
public final class LoginScreen implements Listener {

    private final Lightning plugin;

    public LoginScreen(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidate(@NotNull final PlayerConnectionValidateLoginEvent event) {
        Component message = event.getKickMessage();
        if (message == null) return;
        if (!plugin.settings().reaches(Surface.KICKS)) {
            plugin.debug("A login was refused, but the kicks surface is disabled.");
            return;
        }
        Charged charged = plugin.charger().charge(message, null, Surface.KICKS);
        // Log the original message to help identify the text that a rewire should match.
        plugin.debug("A login was refused" + (charged == null ? ", nothing matched: " : ", charged: ") + Inspector.describe(message));
        if (charged != null) event.kickMessage(Charger.own(charged.component()));
    }
}