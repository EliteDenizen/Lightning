package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.*;

public final class CommandRegistry {

    private final Lightning plugin;
    private final Map<String, CommandHandle> registered = new LinkedHashMap<>();
    private final Map<String, org.bukkit.command.Command> stolenFrom = new HashMap<>();
    private final Set<String> hiddenLabels = new HashSet<>();
    private CommandMap commandMap;

    public CommandRegistry(Lightning plugin) {
        this.plugin = plugin;
        this.commandMap = Bukkit.getCommandMap();
        if (commandMap == null) {
            PluginLogger.warn("Couldn't get a hold of the server's command map, custom commands won't be able to register");
        }
    }

    public void register(CommandHandler executor) {
        CustomCommand cmd = executor.getDefinition();
        CommandHandle handle = new CommandHandle(cmd.getName(), executor);

        List<String> aliases = new ArrayList<>(cmd.getAliases());
        handle.setAliases(aliases);

        if (cmd.getDescription() != null && !cmd.getDescription().isEmpty()) {
            handle.setDescription(cmd.getDescription());
        }

        if (cmd.getUsage() != null && !cmd.getUsage().isEmpty()) {
            handle.setUsage(cmd.getUsage());
        }

        registerPermissionIfNeeded(cmd);

        if (commandMap == null) {
            PluginLogger.warn("No command map to register /{0} with, it won't work until this is sorted out", cmd.getName());
            return;
        }

        commandMap.register(plugin.getName().toLowerCase(), handle);
        claimLabel(cmd.getName(), handle, cmd.shouldOverride());
        for (String alias : aliases) {
            claimLabel(alias, handle, cmd.shouldOverride());
        }
        registered.put(cmd.getName().toLowerCase(), handle);

        if (!cmd.isVisible()) {
            hiddenLabels.add(cmd.getName().toLowerCase());
            for (String alias : aliases) {
                hiddenLabels.add(alias.toLowerCase());
            }
        }
        PluginLogger.debug("Registered /{0} (aliases: {1})", cmd.getName(), aliases);
    }

    private void claimLabel(String label, CommandHandle handle, boolean override) {
        org.bukkit.command.Command current = commandMap.getCommand(label);
        if (current == handle) return;

        if (current != null && !override) {
            PluginLogger.warn("/{0} is already registered by {1}. Lightning's version is still reachable "
                    + "as /lightning:{0}, add \"override: true\" to this command if you want it to take "
                    + "priority instead.", label, current.getClass().getSimpleName());
            return;
        }

        Map<String, org.bukkit.command.Command> knownCommands = getKnownCommands();
        if (knownCommands == null) return;

        if (current != null) {
            stolenFrom.put(label.toLowerCase(), current);
            PluginLogger.debug("/{0} was taken over from {1}", label, current.getClass().getSimpleName());
        }

        knownCommands.put(label.toLowerCase(), handle);
    }

    private void registerPermissionIfNeeded(CustomCommand cmd) {
        String node = cmd.getPermission();
        if (node.isEmpty()) return;

        if (Bukkit.getPluginManager().getPermission(node) != null) {
            PluginLogger.debug("Permission '{0}' already exists, Lightning won't touch it", node);
            return;
        }

        PermissionDefault def = parseDefault(cmd.getPermissionDefault());
        Permission permission = new Permission(node, "Allows using /" + cmd.getName(), def);
        Bukkit.getPluginManager().addPermission(permission);
        PluginLogger.debug("Registered permission node '{0}' for /{1} (default: {2})", node, cmd.getName(), def);
    }

    private PermissionDefault parseDefault(String raw) {
        if (raw == null) return PermissionDefault.FALSE;
        switch (raw.trim().toLowerCase()) {
            case "true": return PermissionDefault.TRUE;
            case "op": return PermissionDefault.OP;
            case "notop": return PermissionDefault.NOT_OP;
            default: return PermissionDefault.FALSE;
        }
    }

    public void unregisterAll() {
        if (registered.isEmpty()) return;

        Map<String, org.bukkit.command.Command> knownCommands = getKnownCommands();
        if (knownCommands == null) {
            PluginLogger.warn("Couldn't reach the server's known-commands list, so unregistering will be incomplete");
            registered.clear();
            return;
        }

        for (CommandHandle handle : registered.values()) {
            handle.unregister(commandMap);
            releaseLabel(handle.getName(), knownCommands);
            for (String alias : handle.getAliases()) {
                releaseLabel(alias, knownCommands);
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + alias);
            }
            knownCommands.remove(plugin.getName().toLowerCase() + ":" + handle.getName());
            PluginLogger.debug("Unregistered /{0}", handle.getName());
        }

        registered.clear();
        hiddenLabels.clear();
    }

    private void releaseLabel(String label, Map<String, org.bukkit.command.Command> knownCommands) {
        String key = label.toLowerCase();
        org.bukkit.command.Command original = stolenFrom.remove(key);
        if (original != null) {
            knownCommands.put(key, original);
            PluginLogger.debug("/{0} was handed back to {1}", label, original.getClass().getSimpleName());
        } else {
            knownCommands.remove(key);
        }
    }

    public int count() { return registered.size(); }
    public Set<String> getRegisteredNames() { return Collections.unmodifiableSet(registered.keySet()); }

    public Set<String> getHiddenLabels() { return Collections.unmodifiableSet(hiddenLabels); }

    private Map<String, org.bukkit.command.Command> getKnownCommands() {
        return commandMap != null ? commandMap.getKnownCommands() : null;
    }
}
