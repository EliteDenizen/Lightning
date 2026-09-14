package dev.elitedenizen.lightning.util.diagnostics;

import dev.elitedenizen.lightning.util.text.ColorFormatter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a GitHub repository's latest release against the running plugin's version and,
 * if a newer one exists, lets the console and permitted players know about it. Written to
 * be dropped into any Spigot/Paper/Folia plugin as is, the only thing tied to Lightning is
 * the package it lives in.
 *
 * Usage:
 * <pre>
 * UpdateChecker.forPlugin(this)
 *         .githubRepo("owner/repo")
 *         .notifyPermission("myplugin.admin")
 *         .start();
 * </pre>
 *
 * The only things this file reaches for outside java.* and org.bukkit.* are PluginLogger and
 * ColorFormatter, both single-purpose one-file helpers. Swap those two calls for your own
 * logger and color code if you're pasting this into a project that doesn't have them.
 */
public final class UpdateChecker implements Listener {

    private static final Pattern REPO_FORMAT = Pattern.compile("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$");
    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]{1,64})\"");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final Plugin plugin;
    private final String apiUrl;
    private final String releasesUrl;
    private final String notifyPermission;
    private final BiConsumer<String, String> onUpdateFound;
    private final long intervalTicks;

    private volatile String latestVersion;
    private CancelHandle recheckTask;

    private UpdateChecker(Builder b) {
        this.plugin = b.plugin;
        this.apiUrl = "https://api.github.com/repos/" + b.repo + "/releases/latest";
        this.releasesUrl = "https://github.com/" + b.repo + "/releases/latest";
        this.notifyPermission = b.notifyPermission;
        this.onUpdateFound = b.onUpdateFound;
        this.intervalTicks = b.intervalTicks;
    }

    public static Builder forPlugin(Plugin plugin) {
        return new Builder(plugin);
    }

    /**
     * Registers the join listener and kicks off the first check. Safe to call once during
     * plugin startup, does nothing dangerous if the network is unreachable or blocked.
     */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        runAsync(this::check);

        if (intervalTicks > 0) {
            recheckTask = scheduleRepeating(this::check, intervalTicks);
        }
    }

    /**
     * Stops any repeating check. Call this from onDisable if a check interval was configured,
     * a one-shot check needs no cleanup.
     */
    public void stop() {
        if (recheckTask != null) {
            recheckTask.cancel();
            recheckTask = null;
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean isUpdateAvailable() {
        return latestVersion != null;
    }

    private void check() {
        HttpURLConnection connection = null;
        try {
            URL url = new URI(apiUrl).toURL();
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                PluginLogger.warn("UpdateChecker refused to use a non-HTTPS URL: {0}", url);
                return;
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", plugin.getName() + "-UpdateChecker");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                PluginLogger.debug("Update check got HTTP {0}, trying again next time", status);
                return;
            }

            String body = readBounded(connection.getInputStream());
            Matcher matcher = TAG_NAME.matcher(body);
            if (!matcher.find()) return;

            String remote = matcher.group(1);
            String current = plugin.getDescription().getVersion();

            if (isNewer(remote, current)) {
                latestVersion = remote;
                runFoliaSafe(() -> {
                    if (onUpdateFound != null) {
                        onUpdateFound.accept(current, remote);
                    } else {
                        announceToConsole(current, remote);
                    }
                });
            }
        } catch (IOException e) {
            PluginLogger.debug("Update check couldn't reach GitHub: {0}", e.getMessage());
        } catch (Exception e) {
            PluginLogger.debug("Something went wrong checking for updates: {0}", e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBounded(InputStream stream) throws IOException {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(4096);
        int read;
        int total = 0;
        try (InputStream in = stream) {
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("response exceeded the " + MAX_RESPONSE_BYTES + " byte limit");
                }
                out.write(buffer, 0, read);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void announceToConsole(String current, String remote) {
        String prefix = "&8[&e" + plugin.getName() + "&8] ";
        Bukkit.getConsoleSender().sendMessage(ColorFormatter.colorize(
                prefix + "&fA newer version is available&7: &e" + current + " &7-> &a" + remote));
        Bukkit.getConsoleSender().sendMessage(ColorFormatter.colorize(prefix + "&7" + releasesUrl));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (latestVersion == null || notifyPermission == null) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(notifyPermission)) return;

        String prefix = "&8[&e" + plugin.getName() + "&8] ";
        player.sendMessage(ColorFormatter.colorize(
                prefix + "&fA newer version is available&7: &a" + latestVersion));
        player.sendMessage(ColorFormatter.colorize(prefix + "&7" + releasesUrl));
    }

    static boolean isNewer(String remote, String current) {
        VersionParts r = VersionParts.parse(remote);
        VersionParts c = VersionParts.parse(current);

        int length = Math.max(r.numbers.length, c.numbers.length);
        for (int i = 0; i < length; i++) {
            int rv = i < r.numbers.length ? r.numbers[i] : 0;
            int cv = i < c.numbers.length ? c.numbers[i] : 0;
            if (rv != cv) return rv > cv;
        }

        if (r.suffix.isEmpty() && !c.suffix.isEmpty()) return true;
        if (!r.suffix.isEmpty() && c.suffix.isEmpty()) return false;
        return r.suffix.compareTo(c.suffix) > 0;
    }

    private static final class VersionParts {
        final int[] numbers;
        final String suffix;

        private VersionParts(int[] numbers, String suffix) {
            this.numbers = numbers;
            this.suffix = suffix;
        }

        static VersionParts parse(String raw) {
            if (raw == null) return new VersionParts(new int[0], "");
            String trimmed = raw.trim();
            if (trimmed.startsWith("v") || trimmed.startsWith("V")) trimmed = trimmed.substring(1);

            int splitAt = trimmed.length();
            for (int i = 0; i < trimmed.length(); i++) {
                char ch = trimmed.charAt(i);
                if (ch != '.' && !Character.isDigit(ch)) {
                    splitAt = i;
                    break;
                }
            }

            String numericPart = trimmed.substring(0, splitAt);
            String suffix = trimmed.substring(splitAt);

            String[] segments = numericPart.split("\\.");
            int[] numbers = new int[segments.length];
            for (int i = 0; i < segments.length; i++) {
                try {
                    numbers[i] = segments[i].isEmpty() ? 0 : Integer.parseInt(segments[i]);
                } catch (NumberFormatException e) {
                    numbers[i] = 0;
                }
            }

            return new VersionParts(numbers, suffix);
        }
    }

    private void runAsync(Runnable task) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                asyncScheduler.getClass()
                        .getMethod("runNow", Plugin.class, java.util.function.Consumer.class)
                        .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) st -> task.run());
                return;
            } catch (ReflectiveOperationException e) {
                PluginLogger.debug("Falling back to the plain Bukkit scheduler: {0}", e.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void runFoliaSafe(Runnable task) {
        if (isFolia()) {
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass()
                        .getMethod("run", Plugin.class, java.util.function.Consumer.class)
                        .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) st -> task.run());
                return;
            } catch (ReflectiveOperationException e) {
                PluginLogger.debug("Falling back to the plain Bukkit scheduler: {0}", e.getMessage());
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private CancelHandle scheduleRepeating(Runnable task, long periodTicks) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                Class<?> asyncClass = asyncScheduler.getClass();
                java.lang.reflect.Method runAtFixedRate = asyncClass.getMethod("runAtFixedRate",
                        Plugin.class, java.util.function.Consumer.class, long.class, long.class, java.util.concurrent.TimeUnit.class);
                long periodMillis = periodTicks * 50L;
                Object handle = runAtFixedRate.invoke(asyncScheduler, plugin,
                        (java.util.function.Consumer<Object>) st -> task.run(),
                        periodMillis, periodMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                java.lang.reflect.Method cancel = handle.getClass().getMethod("cancel");
                return () -> {
                    try {
                        cancel.invoke(handle);
                    } catch (ReflectiveOperationException e) {
                        PluginLogger.debug("Couldn't cancel the update check task: {0}", e.getMessage());
                    }
                };
            } catch (ReflectiveOperationException e) {
                PluginLogger.debug("Falling back to the plain Bukkit scheduler: {0}", e.getMessage());
            }
        }
        org.bukkit.scheduler.BukkitTask bukkitTask =
                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, periodTicks, periodTicks);
        return bukkitTask::cancel;
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface CancelHandle {
        void cancel();
    }

    public static final class Builder {
        private final Plugin plugin;
        private String repo;
        private String notifyPermission;
        private BiConsumer<String, String> onUpdateFound;
        private long intervalTicks = 0;

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        public Builder githubRepo(String ownerSlashRepo) {
            if (ownerSlashRepo == null || !REPO_FORMAT.matcher(ownerSlashRepo).matches()) {
                throw new IllegalArgumentException(
                        "repo must look like \"owner/name\", got: " + ownerSlashRepo);
            }
            this.repo = ownerSlashRepo;
            return this;
        }

        public Builder notifyPermission(String permission) {
            this.notifyPermission = permission;
            return this;
        }

        public Builder onUpdateFound(BiConsumer<String, String> callback) {
            this.onUpdateFound = callback;
            return this;
        }

        public Builder recheckEvery(long ticks) {
            this.intervalTicks = Math.max(0, ticks);
            return this;
        }

        public UpdateChecker build() {
            if (repo == null) throw new IllegalStateException("githubRepo(...) must be set before build()");
            return new UpdateChecker(this);
        }

        public UpdateChecker start() {
            UpdateChecker checker = build();
            checker.start();
            return checker;
        }
    }
}
