package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.tag.Faces;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

final class Clients {

    private Clients() {
    }

    /**
     * Whether the viewer's client can display object components such as player heads and sprites.
     * Objects were added in 1.21.9. Older clients joining through ViaVersion are disconnected when they receive one.
     * Without a viewer, such as on login screens, objects are never sent because the client version is not known.
     */
    static boolean objects(@Nullable final Player viewer) {
        if (!Faces.SUPPORTED || viewer == null) return false;
        try {
            ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(viewer);
            return version != null && version != ClientVersion.UNKNOWN && !version.isOlderThan(ClientVersion.V_1_21_9);
        } catch (Throwable e) {
            // PacketEvents is unavailable, so the server version is the only reference.
            return true;
        }
    }
}