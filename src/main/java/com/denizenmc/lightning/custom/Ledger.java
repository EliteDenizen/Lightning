package com.denizenmc.lightning.custom;

import com.denizenmc.lightning.Lightning;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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
            return list;
        });
        dirty = true;
    }

    void reset(@NotNull final UUID player) {
        uses.keySet().removeIf(key -> key.startsWith(player + "/"));
        dirty = true;
    }

    void load() {
        uses.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String player : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(player);
            if (section == null) continue;
            for (String command : section.getKeys(false)) {
                uses.put(player + "/" + command, new ArrayList<>(section.getLongList(command)));
            }
        }
    }

    void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        uses.forEach((key, times) -> {
            int split = key.indexOf('/');
            yaml.set(key.substring(0, split) + "." + key.substring(split + 1), times);
        });
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Command cooldowns and limits couldn't be saved: " + e.getMessage());
        }
    }

    // Replace dots in command paths so they don't create nested YAML keys.
    private static String key(final UUID player, final Spec spec) {
        return player + "/" + spec.path().replace('.', '-');
    }
}