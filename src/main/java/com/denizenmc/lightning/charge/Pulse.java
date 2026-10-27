package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.Lightning;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// Keeps animations and timed placeholders updating on lines sent only once, such as static scoreboard lines.
// Resent packets bypass Lightning's outgoing packet listener to prevent recursive processing.
public final class Pulse {

    private static final int LIMIT = 256;

    private final Lightning plugin;
    private final Map<UUID, Map<String, Beat>> beats = new ConcurrentHashMap<>();
    private ScheduledTask task;

    public Pulse(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long every = plugin.settings().pulse();
        if (every <= 0) return;
        task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> beat(), every, every, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        beats.clear();
    }

    /**
     * Tracks a line for periodic reprocessing and resending.
     *
     * @param slot      identifies the line; a new entry for the same slot replaces the existing one
     * @param originals the original components received from the plugin
     * @param shown     the components after Lightning processes them
     * @param packet    builds a packet containing the updated components in the same order as the originals
     */
    public void track(@NotNull final User user, @NotNull final Player viewer, @NotNull final Surface surface, @NotNull final String slot,
                      @NotNull final List<Component> originals, @NotNull final List<Component> shown,
                      @NotNull final Function<List<Component>, PacketWrapper<?>> packet) {
        if (task == null) return;
        Map<String, Beat> lines = beats.computeIfAbsent(viewer.getUniqueId(), id -> new ConcurrentHashMap<>());
        if (lines.size() >= LIMIT && !lines.containsKey(slot)) return;
        lines.put(slot, new Beat(user, viewer, surface, new ArrayList<>(originals), shown, packet));
    }

    public void forget(@NotNull final UUID viewer, @NotNull final String slot) {
        Map<String, Beat> lines = beats.get(viewer);
        if (lines != null) lines.remove(slot);
    }

    public void forgetStarting(@NotNull final UUID viewer, @NotNull final String prefix) {
        Map<String, Beat> lines = beats.get(viewer);
        if (lines != null) lines.keySet().removeIf(slot -> slot.startsWith(prefix));
    }

    public void forget(@NotNull final UUID viewer) {
        beats.remove(viewer);
    }

    private void beat() {
        for (Map.Entry<UUID, Map<String, Beat>> entry : beats.entrySet()) {
            for (Map.Entry<String, Beat> line : entry.getValue().entrySet()) {
                Beat beat = line.getValue();
                if (!beat.viewer.isOnline()) {
                    beats.remove(entry.getKey());
                    break;
                }
                try {
                    List<Component> fresh = recharge(beat);
                    if (fresh.equals(beat.shown)) continue;
                    beat.shown = fresh;
                    beat.user.sendPacketSilently(beat.packet.apply(fresh));
                } catch (RuntimeException e) {
                    entry.getValue().remove(line.getKey());
                    plugin.debug("Stopped animating a " + beat.surface.key() + " line: " + e.getMessage());
                }
            }
        }
    }

    private List<Component> recharge(final Beat beat) {
        List<Component> fresh = new ArrayList<>(beat.originals.size());
        for (Component original : beat.originals) {
            Charged charged = original == null ? null : plugin.charger().charge(original, beat.viewer, beat.surface);
            fresh.add(charged == null ? original : charged.component());
        }
        return fresh;
    }

    private static final class Beat {

        private final User user;
        private final Player viewer;
        private final Surface surface;
        private final List<Component> originals;
        private final Function<List<Component>, PacketWrapper<?>> packet;
        private volatile List<Component> shown;

        private Beat(final User user, final Player viewer, final Surface surface, final List<Component> originals,
                     final List<Component> shown, final Function<List<Component>, PacketWrapper<?>> packet) {
            this.user = user;
            this.viewer = viewer;
            this.surface = surface;
            this.originals = originals;
            this.shown = shown;
            this.packet = packet;
        }
    }
}