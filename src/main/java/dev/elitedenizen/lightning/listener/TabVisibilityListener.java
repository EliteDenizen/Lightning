package dev.elitedenizen.lightning.listener;

import dev.elitedenizen.lightning.Lightning;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Iterator;
import java.util.Set;

public final class TabVisibilityListener implements Listener {

    private final Lightning plugin;

    public TabVisibilityListener(Lightning plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Set<String> hidden = plugin.getCommandRegistry().getHiddenLabels();
        if (hidden.isEmpty()) return;

        String prefix = plugin.getName().toLowerCase() + ":";
        Iterator<String> iterator = event.getCommands().iterator();

        while (iterator.hasNext()) {
            String entry = iterator.next();
            String bare = entry.startsWith(prefix) ? entry.substring(prefix.length()) : entry;
            if (hidden.contains(bare.toLowerCase())) {
                iterator.remove();
            }
        }
    }
}
