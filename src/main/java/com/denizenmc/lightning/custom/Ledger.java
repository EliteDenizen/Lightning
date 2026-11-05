package com.denizenmc.lightning.custom;

import com.denizenmc.lightning.Lightning;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Tracks command usage per player, retaining only the timestamps needed for cooldowns and limits.
final class Ledger {

    private final Lightning plugin;
    private final File file;
    private final Map<String, List<Long>> uses = new ConcurrentHashMap<>();
    // How long each command's timestamps matter, keyed like the stored entries. 0 means forever.
    private volatile Map<String, Long> retention = Map.of();
    private volatile boolean dirty;

    Ledger(@NotNull final Lightning plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/uses.yml");
    }

    long cooldownLeft(@NotNull final UUID player, @NotNull final Spec spec) {
        if (spec.cooldown() <= 0) return 0;
        List<Long> times = uses.get(key(player, spec));
        if (times == null || times.isEmpty()) return 0;
        return Math.max(0, times.get(times.size() - 1) + spec.cooldown() - System.currentTimeMillis());
    }

    boolean limitReached(@NotNull final UUID player, @NotNull final Spec spec) {
        Spec.Limit limit = spec.limit();
        if (limit == null) return false;
        List<Long> times = uses.get(key(player, spec));
        if (times == null || times.size() < limit.amount()) return false;
        if (limit.window() == 0) return true;
        long oldest = times.get(times.size() - limit.amount());
        return System.currentTimeMillis() - oldest < limit.window();
    }

    void record(@NotNull final UUID player, @NotNull final Spec spec) {
        if (spec.cooldown() <= 0 && spec.limit() == null) return;
        int keep = spec.limit() == null ? 1 : Math.max(1, spec.limit().amount());
        uses.compute(key(player, spec), (key, times) -> {
            List<Long> list = times == null ? new ArrayList<>() : new ArrayList<>(times);
            list.add(System.currentTimeMillis());
            while (list.size() > keep) list.remove(0);
            return List.copyOf(list);
        });
        dirty = true;
    }

    void reset(@NotNull final UUID player) {
        uses.keySet().removeIf(key -> key.startsWith(player + "/"));
        dirty = true;
    }

    // Called after commands load, so entries for commands whose cooldown has long passed can be dropped.
    void retain(@NotNull final Map<String, Long> windows) {
        retention = Map.copyOf(windows);
    }

    synchronized void load() {
        uses.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String player : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(player);
            if (section == null) continue;
            for (String command : section.getKeys(false)) {
                uses.put(player + "/" + command, List.copyOf(section.getLongList(command)));
            }
        }
    }

    // Saves can run from the autosave task and from shutdown at the same time, so they take turns,
    // and the file is replaced in one step so a crash mid-save cannot leave it half written.
    synchronized void save() {
        prune();
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        uses.forEach((key, times) -> {
            int split = key.indexOf('/');
            yaml.set(key.substring(0, split) + "." + key.substring(split + 1), times);
        });
        Path target = file.toPath();
        Path temporary = target.resolveSibling(file.getName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().warning("Command cooldowns and limits couldn't be saved: " + e.getMessage());
        }
    }

    // Removes uses that no longer affect a cooldown or limit. Commands that are no longer configured are kept,
    // so temporarily disabling a command does not let players claim a one-time reward again.
    private void prune() {
        Map<String, Long> windows = retention;
        if (windows.isEmpty()) return;
        long now = System.currentTimeMillis();
        uses.replaceAll((key, times) -> {
            Long window = windows.get(key.substring(key.indexOf('/') + 1));
            if (window == null || window == 0) return times;
            List<Long> kept = times.stream().filter(time -> now - time < window).toList();
            if (kept.size() != times.size()) dirty = true;
            return kept;
        });
        if (uses.values().removeIf(List::isEmpty)) dirty = true;
    }

    // Replace dots in command paths so they don't create nested YAML keys.
    static String path(final Spec spec) {
        return spec.path().replace('.', '-');
    }

    private static String key(final UUID player, final Spec spec) {
        return player + "/" + path(spec);
    }
}
