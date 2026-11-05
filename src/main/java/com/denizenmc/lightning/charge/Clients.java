package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.tag.Faces;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

final class Clients {

    // Protocol number of Minecraft 1.21.9, the first version that can display object components.
    private static final int OBJECTS_PROTOCOL = 773;
    private static final int NO_VIA = Integer.MIN_VALUE;

    private static volatile boolean packetEvents = true;
    private static volatile Method viaVersion;
    private static volatile Object viaApi;
    private static volatile boolean viaLooked;

    private Clients() {
    }

    /**
     * Whether the viewer's client can display object components such as player heads and sprites.
     * Objects were added in 1.21.9. Older clients joining through ViaVersion are disconnected when they receive one.
     * Without a viewer, such as on login screens, objects are never sent because the client version is not known.
     */
    static boolean objects(@Nullable final Player viewer) {
        if (!Faces.SUPPORTED || viewer == null) return false;
        if (packetEvents) {
            try {
                ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(viewer);
                // An unknown version is treated as too old, since sending objects to an old client disconnects it.
                return version != null && version != ClientVersion.UNKNOWN && !version.isOlderThan(ClientVersion.V_1_21_9);
            } catch (Throwable e) {
                // PacketEvents is missing or failed to load; ask ViaVersion instead from now on.
                packetEvents = false;
            }
        }
        int protocol = viaProtocol(viewer.getUniqueId());
        // Without ViaVersion every client must match the server's version, which supports objects.
        if (protocol == NO_VIA) return true;
        return protocol >= OBJECTS_PROTOCOL;
    }

    // Reads the client's protocol from ViaVersion through reflection, so Lightning never needs it at compile time.
    private static int viaProtocol(final UUID player) {
        if (!viaLooked) {
            if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null) {
                try {
                    ClassLoader loader = Bukkit.getPluginManager().getPlugin("ViaVersion").getClass().getClassLoader();
                    Class<?> via = Class.forName("com.viaversion.viaversion.api.Via", true, loader);
                    Class<?> type = Class.forName("com.viaversion.viaversion.api.ViaAPI", true, loader);
                    viaApi = via.getMethod("getAPI").invoke(null);
                    viaVersion = type.getMethod("getPlayerVersion", UUID.class);
                } catch (Throwable ignored) {
                    viaVersion = null;
                }
            }
            viaLooked = true;
        }
        Method method = viaVersion;
        if (method == null) return NO_VIA;
        try {
            Object version = method.invoke(viaApi, player);
            return version instanceof Integer number ? number : -1;
        } catch (Throwable e) {
            return -1;
        }
    }
}
