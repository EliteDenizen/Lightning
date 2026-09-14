package dev.elitedenizen.lightning.formatting;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

public final class LightningPacketEvents {

    private final Lightning plugin;
    private MessageInterceptor messageInterceptor;
    private TablistListener tablistListener;
    private boolean loaded;

    public LightningPacketEvents(Lightning plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
            PacketEvents.getAPI().getSettings()
                    .reEncodeByDefault(true)
                    .bStats(false)
                    .checkForUpdates(false);
            PacketEvents.getAPI().load();
            loaded = true;
        } catch (Throwable e) {
            PluginLogger.error("Couldn't load PacketEvents, the config helper feature won't be available this run", e);
        }
    }

    public void enable() {
        if (!loaded) return;
        try {
            messageInterceptor = new MessageInterceptor(plugin);
            tablistListener = new TablistListener(plugin);
            PacketEvents.getAPI().getEventManager().registerListener(messageInterceptor, PacketListenerPriority.NORMAL);
            PacketEvents.getAPI().getEventManager().registerListener(tablistListener, PacketListenerPriority.NORMAL);
            PacketEvents.getAPI().init();
        } catch (Throwable e) {
            PluginLogger.error("Couldn't start PacketEvents, the config helper feature won't be available this run", e);
        }
    }

    public void disable() {
        if (!loaded) return;
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable e) {
            PluginLogger.error("PacketEvents didn't shut down cleanly", e);
        }
    }
}
