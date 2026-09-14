package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UsageTracker {

    private static final long SAVE_INTERVAL_TICKS = 6000L;
    private static final long MAX_RECORD_AGE_MS = 30L * 24 * 60 * 60 * 1000L;

    private final Lightning plugin;
    private final Map<String, PlayerRecord> records = new ConcurrentHashMap<>();
    private final File dataFile;

    private SchedulerBridge.CancellableTask saveTask;
    private volatile boolean dirty = false;

    public UsageTracker(Lightning plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/usage.yml");
        load();
        scheduleSave();
    }

    public int getUseCount(UUID player, CustomCommand cmd) {
        PlayerRecord r = records.get(usesKey(player, cmd));
        return r != null ? r.count : 0;
    }

    public int getUseCount(UUID player, String command) {
        PlayerRecord r = records.get("uses:" + player + ":" + command.toLowerCase());
        return r != null ? r.count : 0;
    }

    public boolean hasCooldown(UUID player, CustomCommand cmd) {
        if (!cmd.hasCooldown()) return false;
        long last = getLastCooldownUse(player, cmd);
        if (last == 0) return false;
        boolean onCooldown = (System.currentTimeMillis() - last) < cmd.getCooldownMs();
        if (onCooldown) {
            PluginLogger.debug("{0} is still on cooldown for /{1} (group: {2}), {3}ms left",
                    player, cmd.getName(), cmd.getCooldownGroup(), getCooldownRemaining(player, cmd));
        }
        return onCooldown;
    }

    public long getCooldownRemaining(UUID player, CustomCommand cmd) {
        long last = getLastCooldownUse(player, cmd);
        if (last == 0) return 0;
        return Math.max(0, cmd.getCooldownMs() - (System.currentTimeMillis() - last));
    }

    public void startCooldown(UUID player, CustomCommand cmd) {
        touch(cooldownKey(player, cmd));
    }

    public boolean hasReachedUsesLimit(UUID player, CustomCommand cmd) {
        if (!cmd.hasUsesLimit()) return false;

        UUID scopedId = cmd.getUsesScope() == CustomCommand.UsesScope.GLOBAL ? globalId() : player;
        String key = "uses:" + scopedId + ":" + cmd.getName().toLowerCase();

        if (cmd.hasUsesReset()) {
            PlayerRecord record = records.get(key);
            if (record != null && (System.currentTimeMillis() - record.lastUse) >= cmd.getUsesResetMs()) {
                records.remove(key);
                dirty = true;
                PluginLogger.debug("Uses for /{0} reset for {1} after {2}ms of inactivity",
                        cmd.getName(), scopedId, cmd.getUsesResetMs());
                return false;
            }
        }

        PlayerRecord record = records.get(key);
        int used = record != null ? record.count : 0;
        boolean reached = used >= cmd.getUsesLimit();
        if (reached) {
            PluginLogger.debug("{0} reached the uses limit for /{1} ({2}/{3}, scope: {4})",
                    player, cmd.getName(), used, cmd.getUsesLimit(), cmd.getUsesScope());
        }
        return reached;
    }

    public void recordUse(UUID player, CustomCommand cmd) {
        UUID scopedId = cmd.getUsesScope() == CustomCommand.UsesScope.GLOBAL ? globalId() : player;
        touch("uses:" + scopedId + ":" + cmd.getName().toLowerCase());
    }

    public void reset(UUID player, String command) {
        boolean removed = records.remove("uses:" + player + ":" + command.toLowerCase()) != null;
        removed |= records.remove("cooldown:" + player + ":" + command.toLowerCase()) != null;
        if (removed) dirty = true;
    }

    public void resetAll(String command) {
        String usesSuffix = ":" + command.toLowerCase();
        boolean removed = records.entrySet().removeIf(e -> e.getKey().endsWith(usesSuffix));
        if (removed) dirty = true;
    }

    private long getLastCooldownUse(UUID player, CustomCommand cmd) {
        PlayerRecord r = records.get(cooldownKey(player, cmd));
        return r != null ? r.lastUse : 0L;
    }

    private void touch(String key) {
        PlayerRecord existing = records.get(key);
        if (existing != null) {
            existing.count++;
            existing.lastUse = System.currentTimeMillis();
        } else {
            records.put(key, new PlayerRecord(1, System.currentTimeMillis()));
        }
        dirty = true;
    }

    private void scheduleSave() {
        saveTask = SchedulerBridge.runTimerAsync(plugin, () -> {
            pruneStaleRecords();
            if (dirty) save();
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    private void pruneStaleRecords() {
        long now = System.currentTimeMillis();
        boolean removed = records.entrySet().removeIf(e -> (now - e.getValue().lastUse) > MAX_RECORD_AGE_MS);
        if (removed) dirty = true;
    }

    public void save() {
        if (!dirty) return;
        try {
            dataFile.getParentFile().mkdirs();
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, PlayerRecord> e : records.entrySet()) {
                String path = e.getKey().replace(".", "_");
                cfg.set(path + ".count", e.getValue().count);
                cfg.set(path + ".last-use", e.getValue().lastUse);
            }
            cfg.save(dataFile);
            dirty = false;
            PluginLogger.debug("Saved {0} usage record(s) to disk", records.size());
        } catch (IOException e) {
            PluginLogger.error("Could not save data/usage.yml, usage records may be lost on restart", e);
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            long now = System.currentTimeMillis();
            int loaded = 0, skipped = 0;
            for (String rawKey : cfg.getKeys(false)) {
                long lastUse = cfg.getLong(rawKey + ".last-use", 0L);
                if (lastUse > 0 && (now - lastUse) > MAX_RECORD_AGE_MS) {
                    skipped++;
                    continue;
                }
                int count = cfg.getInt(rawKey + ".count", 0);
                records.put(rawKey, new PlayerRecord(count, lastUse));
                loaded++;
            }
            if (skipped > 0) {
                PluginLogger.debug("Dropped {0} usage record(s) older than 30 days on load", skipped);
                dirty = true;
            }
            PluginLogger.debug("Loaded {0} usage record(s) from disk", loaded);
        } catch (Exception e) {
            PluginLogger.error("Could not read data/usage.yml, starting with empty usage records", e);
        }
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        save();
    }

    private static UUID globalId() {
        return new UUID(0L, 0L);
    }

    private static String cooldownKey(UUID player, CustomCommand cmd) {
        return "cooldown:" + player + ":" + cmd.getCooldownGroup();
    }

    private static String usesKey(UUID player, CustomCommand cmd) {
        return "uses:" + player + ":" + cmd.getName().toLowerCase();
    }

    private static final class PlayerRecord {
        int count;
        long lastUse;

        PlayerRecord(int count, long lastUse) {
            this.count = count;
            this.lastUse = lastUse;
        }
    }
}
