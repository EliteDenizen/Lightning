package dev.elitedenizen.lightning;

import dev.elitedenizen.lightning.action.ActionLoader;
import dev.elitedenizen.lightning.action.TemplateManager;
import dev.elitedenizen.lightning.command.CommandHandler;
import dev.elitedenizen.lightning.command.CommandLoader;
import dev.elitedenizen.lightning.command.CommandRegistry;
import dev.elitedenizen.lightning.command.AdminCommand;
import dev.elitedenizen.lightning.command.CustomCommand;
import dev.elitedenizen.lightning.command.UsageTracker;
import dev.elitedenizen.lightning.listener.HookListener;
import dev.elitedenizen.lightning.listener.TabVisibilityListener;
import dev.elitedenizen.lightning.resolver.OwnerResolver;
import dev.elitedenizen.lightning.rule.HookRuleLoader;
import dev.elitedenizen.lightning.rule.RuleBook;
import dev.elitedenizen.lightning.settings.PluginConfig;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.text.Messages;
import dev.elitedenizen.lightning.util.text.LightningExpansion;
import dev.elitedenizen.lightning.util.scheduling.SchedulerBridge;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import dev.elitedenizen.lightning.util.diagnostics.UpdateChecker;
import dev.elitedenizen.lightning.util.server.VersionAdapter;
import dev.elitedenizen.lightning.formatting.LightningPacketEvents;
import dev.elitedenizen.lightning.metrics.Metrics;
import dev.elitedenizen.lightning.metrics.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Lightning extends JavaPlugin {

    private static Lightning instance;

    private Messages messages;
    private TemplateManager templateManager;
    private PluginConfig settings;
    private RuleBook ruleBook;
    private OwnerResolver ownerResolver;
    private CommandRegistry commandRegistry;
    private UsageTracker usageTracker;
    private LightningExpansion placeholderExpansion;
    private boolean firstInstall;
    private LightningPacketEvents packetEvents;
    private Metrics metrics;

    private final Map<String, CommandHandler> handlers = new LinkedHashMap<>();

    @Override
    public void onLoad() {
        packetEvents = new LightningPacketEvents(this);
        packetEvents.load();
    }

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        firstInstall = markFirstInstall();

        PluginLogger.init(this);
        logColored("&f[Lightning] Starting &eLightning&f...");

        VersionAdapter.ParsedVersion version = VersionAdapter.parseVersion();
        logColored("&f[Lightning] Identified server version to be &6" + VersionAdapter.serverBrand() + " " + version.friendly());

        settings = new PluginConfig(this);
        messages = new Messages(this);
        templateManager = new TemplateManager();
        ActionLoader.setTemplateManager(templateManager);
        ruleBook = new RuleBook();
        ownerResolver = new OwnerResolver(this);
        SchedulerBridge.runTimerAsync(this, ownerResolver::pruneExpired, 12000L, 12000L);
        commandRegistry = new CommandRegistry(this);
        usageTracker = new UsageTracker(this);

        packetEvents.enable();
        installDefaultFiles();
        loadTemplates();
        loadHooks();
        loadCommands();
        firstInstall = false;

        logColored("&f[Lightning] Loaded &e" + templateManager.count() + " &ftemplate(s), &e" + commandRegistry.count()
                + " &fcustom command(s), &e" + ruleBook.size() + " &faction(s)");

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            logColored("&f[Lightning] Hooking into &dPlaceholderAPI&f...");
            placeholderExpansion = new LightningExpansion(this);
            placeholderExpansion.register();
        }

        getServer().getPluginManager().registerEvents(new HookListener(this), this);
        getServer().getPluginManager().registerEvents(new TabVisibilityListener(this), this);

        setupMetrics();

        if (getConfig().getBoolean("checkForUpdates", true)) {
            UpdateChecker.forPlugin(this)
                    .githubRepo("elitedenizen/lightning")
                    .notifyPermission("lightning.admin")
                    .start();
        }

        AdminCommand admin = new AdminCommand(this);
        getCommand("lightning").setExecutor(admin);
        getCommand("lightning").setTabCompleter(admin);
    }

    private void setupMetrics() {
        metrics = new Metrics(this, 21);
        metrics.addCustomChart(new SimplePie("configHelperEnabled",
                () -> String.valueOf(settings.isConfigHelperEnabled())));
        metrics.addCustomChart(new SimplePie("customCommandCount",
                () -> bucket(commandRegistry.count())));
        metrics.addCustomChart(new SimplePie("hookRuleCount",
                () -> bucket(ruleBook.size())));
    }

    private static String bucket(int count) {
        if (count == 0) return "0";
        if (count <= 5) return "1-5";
        if (count <= 20) return "6-20";
        return "20+";
    }

    private void logColored(String message) {
        Bukkit.getConsoleSender().sendMessage(ColorFormatter.colorize(message));
    }

    @Override
    public void onDisable() {
        if (metrics != null) metrics.shutdown();
        if (packetEvents != null) packetEvents.disable();
        if (usageTracker != null) usageTracker.shutdown();
        if (commandRegistry != null) commandRegistry.unregisterAll();
        if (placeholderExpansion != null) placeholderExpansion.unregister();
        handlers.clear();
        instance = null;
    }

    public void fullReload() {
        long start = PluginLogger.startTimer();

        commandRegistry.unregisterAll();
        handlers.clear();
        ownerResolver.invalidate();

        settings.reload();
        messages.reload();
        PluginLogger.reload(this);

        templateManager.clear();
        loadTemplates();
        loadHooks();
        loadCommands();

        PluginLogger.logTime(start, "Full reload");
    }

    private boolean markFirstInstall() {
        File marker = new File(getDataFolder(), ".installed");
        if (marker.exists()) return false;
        try {
            marker.createNewFile();
        } catch (java.io.IOException e) {
            PluginLogger.debug("Could not write the install marker, examples may reappear after a reload");
        }
        return true;
    }

    public boolean isFirstInstall() {
        return firstInstall;
    }

    private void installDefaultFiles() {
        if (!firstInstall) return;
        trySave("templates/teleport.yml");
        trySave("templates/feedback.yml");
    }

    private void loadTemplates() {
        File folder = new File(getDataFolder(), "templates");
        folder.mkdirs();
        templateManager.loadFromFolder(folder);
    }

    private void loadHooks() {
        ruleBook.setRules(HookRuleLoader.loadFromFolder(this));
    }

    private void loadCommands() {
        List<CustomCommand> commands = CommandLoader.loadFromFolder(this);
        Map<String, String> claimedBy = new HashMap<>();

        for (CustomCommand cmd : commands) {
            checkForClash(cmd, cmd.getAllNames(), claimedBy);

            CommandHandler handler = new CommandHandler(this, cmd, usageTracker);
            commandRegistry.register(handler);
            handlers.put(cmd.getName(), handler);
            for (String alias : cmd.getAliases()) {
                handlers.put(alias.toLowerCase(), handler);
            }
        }
    }

    private void checkForClash(CustomCommand cmd, List<String> labels, Map<String, String> claimedBy) {
        boolean clash = false;
        for (String label : labels) {
            String previousFile = claimedBy.get(label.toLowerCase());
            if (previousFile != null && !previousFile.equals(cmd.getSourceFile())) {
                PluginLogger.warn("The name '{0}' is claimed by both {1} and {2}, "
                        + "the one from {2} is what will actually run since it loaded last",
                        label, previousFile, cmd.getSourceFile());
                clash = true;
            }
        }
        if (clash) {
            PluginLogger.warn("Rename one of the conflicting commands above so both can work");
        }
        for (String label : labels) {
            claimedBy.put(label.toLowerCase(), cmd.getSourceFile());
        }
    }

    public CustomCommand findCommand(String name) {
        if (name == null || name.isEmpty()) return null;
        CommandHandler handler = handlers.get(name.toLowerCase().trim());
        return handler != null ? handler.getDefinition() : null;
    }

    private void trySave(String path) {
        File target = new File(getDataFolder(), path);
        if (target.exists()) return;
        try {
            saveResource(path, false);
        } catch (IllegalArgumentException ignored) {}
    }

    public static Lightning get() { return instance; }
    public Messages getMessages() { return messages; }
    public TemplateManager getTemplateManager() { return templateManager; }
    public PluginConfig getPluginConfig() { return settings; }
    public RuleBook getRuleBook() { return ruleBook; }
    public OwnerResolver getOwnerResolver() { return ownerResolver; }
    public CommandRegistry getCommandRegistry() { return commandRegistry; }
    public UsageTracker getUsageTracker() { return usageTracker; }
}
