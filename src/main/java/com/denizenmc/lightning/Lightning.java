package com.denizenmc.lightning;

import com.denizenmc.lightning.charge.Charger;
import com.denizenmc.lightning.charge.Guard;
import com.denizenmc.lightning.charge.Pulse;
import com.denizenmc.lightning.command.LightningCommand;
import com.denizenmc.lightning.custom.CommandBook;
import com.denizenmc.lightning.tool.Converter;
import com.denizenmc.lightning.tool.Doctor;
import com.denizenmc.lightning.listener.Inspector;
import com.denizenmc.lightning.listener.LegacyLoginScreen;
import com.denizenmc.lightning.listener.LoginScreen;
import com.denizenmc.lightning.listener.PlayerWatch;
import com.denizenmc.lightning.listener.SurfaceListener;
import com.denizenmc.lightning.metrics.Metrics;
import com.denizenmc.lightning.rewire.Rewires;
import com.denizenmc.lightning.spark.Sparks;
import com.denizenmc.lightning.tag.Faces;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

public final class Lightning extends JavaPlugin {

    private Settings settings;
    private Sparks sparks;
    private Rewires rewires;
    private CommandBook commands;
    private Doctor doctor;
    private Converter converter;
    private Inspector inspector;
    private Guard guard;
    private Faces faces;
    private Charger charger;
    private Pulse pulse;
    private Metrics metrics;
    private boolean packets;
    private final java.util.Map<String, Long> recentDebug = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().getSettings().reEncodeByDefault(false).checkForUpdates(false).bStats(false);
            PacketEvents.getAPI().load();
            packets = true;
        } catch (Throwable e) {
            getLogger().log(Level.SEVERE, "PacketEvents failed to load. Messages from other plugins cannot be processed during this session.", e);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "sparks").exists()) {
            saveResource("sparks/theme.yml", false);
            saveResource("sparks/server.yml", false);
        }
        if (!new File(getDataFolder(), "rewires").exists()) saveResource("rewires/examples.yml", false);
        if (!new File(getDataFolder(), "commands").exists()) saveResource("commands/examples.yml", false);

        settings = new Settings(this);
        sparks = new Sparks(this);
        rewires = new Rewires(this);
        commands = new CommandBook(this);
        doctor = new Doctor(this);
        converter = new Converter(this);
        inspector = new Inspector(this);
        guard = new Guard(this);
        faces = new Faces(this);
        charger = new Charger(this);
        pulse = new Pulse(this);
        load();

        getServer().getPluginManager().registerEvents(new PlayerWatch(this), this);

        // Check for the newer login event before loading its listener to maintain compatibility with older server versions.
        getServer().getPluginManager().registerEvents(classExists("io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent")
                ? new LoginScreen(this) : new LegacyLoginScreen(this), this);

        if (packets) {
            PacketEvents.getAPI().getEventManager().registerListener(new SurfaceListener(this));
            PacketEvents.getAPI().init();
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("lightning", "Reload, test and inspect Lightning.", List.of("lt"), new LightningCommand(this));
            commands.register(event.registrar());
        });
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> commands.save(), 5, 5, java.util.concurrent.TimeUnit.MINUTES);

        try {
            metrics = new Metrics(this, 21);
        } catch (Throwable e) {
            debug("Metrics initialization failed: " + e.getMessage());
        }

        getLogger().info("Running on " + getServer().getName() + " " + getServer().getMinecraftVersion()
                + (Faces.SUPPORTED ? "." : ". Player faces require Minecraft 1.21.9 or newer; using the configured fallback."));
        if (!packets) getLogger().warning("PacketEvents is unavailable. Custom commands remain enabled, but packet-based message processing is disabled.");
    }

    @Override
    public void onDisable() {
        if (pulse != null) pulse.stop();
        if (commands != null) commands.save();
        if (metrics != null) metrics.shutdown();
        if (packets) PacketEvents.getAPI().terminate();
    }

    private static boolean classExists(final String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public void load() {
        long started = System.nanoTime();
        settings.load();
        sparks.load();
        rewires.load();
        commands.load();
        faces.clear();
        charger.rebuild();
        if (packets) pulse.start();
        getLogger().info("Loaded " + count(sparks.names().size(), "spark") + ", " + count(rewires.size(), "rewire") + " and "
                + count(commands.size(), "command") + " in " + (System.nanoTime() - started) / 1_000_000 + "ms.");
    }

    private static String count(final int amount, final String word) {
        return amount + " " + word + (amount == 1 ? "" : "s");
    }

    public @NotNull Component message(@NotNull final String key, @NotNull final TagResolver... extra) {
        return compose(settings.message(key), extra);
    }

    public @NotNull Component compose(@NotNull final String text, @NotNull final TagResolver... extra) {
        TagResolver prefix = Placeholder.parsed("prefix", settings.message("prefix"));
        // Apply a neutral base color so message text remains readable while explicitly formatted values retain their styling.
        return Component.text().color(TextColor.color(0xCFCFCF))
                .append(MiniMessage.miniMessage().deserialize(text, TagResolver.resolver(prefix, TagResolver.resolver(extra))))
                .build();
    }

    // Suppress repeated debug messages for five seconds to prevent frequently updated surfaces from flooding the console.
    public void debug(@NotNull final String message) {
        if (settings == null || !settings.debug()) return;
        long now = System.currentTimeMillis();
        Long last = recentDebug.get(message);
        if (last != null && now - last < 5_000L) return;
        if (recentDebug.size() > 256) recentDebug.clear();
        recentDebug.put(message, now);
        getLogger().info("[Debug] " + message);
    }

    public @NotNull Settings settings() {
        return settings;
    }

    public @NotNull Sparks sparks() {
        return sparks;
    }

    public @NotNull Rewires rewires() {
        return rewires;
    }

    public @NotNull CommandBook commands() {
        return commands;
    }

    public @NotNull Doctor doctor() {
        return doctor;
    }

    public @NotNull Converter converter() {
        return converter;
    }

    public @NotNull Inspector inspector() {
        return inspector;
    }

    public @NotNull Guard guard() {
        return guard;
    }

    public @NotNull Faces faces() {
        return faces;
    }

    public @NotNull Pulse pulse() {
        return pulse;
    }

    public @NotNull Charger charger() {
        return charger;
    }
}
