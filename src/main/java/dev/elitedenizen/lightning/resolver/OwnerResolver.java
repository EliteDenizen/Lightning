package dev.elitedenizen.lightning.resolver;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.cache.ExpiringCache;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public final class OwnerResolver {

    private final Lightning plugin;
    private final ExpiringCache<String, CommandOwner> cache = ExpiringCache.withLifetime(10, TimeUnit.MINUTES);
    private CommandMap commandMap;

    public OwnerResolver(Lightning plugin) {
        this.plugin = plugin;
        commandMap = Bukkit.getCommandMap();
    }

    public void invalidate() {
        cache.clear();
        commandMap = Bukkit.getCommandMap();
        PluginLogger.debug("Owner cache cleared and the command map reference was refreshed");
    }

    public CommandOwner resolve(String commandLabel) {
        return resolve(commandLabel, null);
    }

    public CommandOwner resolve(String commandLabel, String typedNamespace) {
        if (commandLabel == null || commandLabel.isEmpty()) return CommandOwner.UNKNOWN;

        String lower = commandLabel.toLowerCase().trim();
        if (lower.contains(":")) lower = lower.substring(lower.indexOf(':') + 1);

        if (typedNamespace != null && !typedNamespace.isEmpty()) {
            CommandOwner namespaced = resolveByNamespace(typedNamespace, lower);
            if (namespaced != null) return namespaced;
        }

        CommandOwner found = cache.getOrCompute(lower, key -> {
            CommandOwner result = doResolve(key);
            return result.isFound() ? result : null;
        });

        return found != null ? found : CommandOwner.UNKNOWN;
    }

    private CommandOwner resolveByNamespace(String namespace, String label) {
        if (namespace.equals("minecraft") || namespace.equals("bukkit")) {
            PluginLogger.debug("/{0} was typed with an explicit minecraft: namespace, trusting that", label);
            return new CommandOwner("Minecraft", "minecraft", true);
        }

        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate.getName().equalsIgnoreCase(namespace)) {
                PluginLogger.debug("/{0} was typed with an explicit {1}: namespace, trusting that", label, candidate.getName());
                return new CommandOwner(candidate.getName(), namespace, true);
            }
        }

        return null;
    }

    private CommandOwner doResolve(String label) {
        if (commandMap == null) return CommandOwner.UNKNOWN;

        Command cmd = commandMap.getCommand(label);
        if (cmd == null) {
            PluginLogger.debug("Nothing on this server owns a command called /{0}", label);
            return CommandOwner.UNKNOWN;
        }

        if (cmd instanceof PluginCommand) {
            Plugin owner = ((PluginCommand) cmd).getPlugin();
            String namespace = owner.getName().toLowerCase();
            PluginLogger.debug("/{0} belongs to {1}", label, owner.getName());
            return new CommandOwner(owner.getName(), namespace, true);
        }

        if (cmd.getClass().getName().startsWith(plugin.getClass().getPackage().getName())) {
            PluginLogger.debug("/{0} is one of Lightning's own commands", label);
            return new CommandOwner(plugin.getName(), plugin.getName().toLowerCase(), true);
        }

        if (commandMap.getCommand("minecraft:" + label) != null) {
            PluginLogger.debug("/{0} is a vanilla Minecraft command", label);
            return new CommandOwner("Minecraft", "minecraft", true);
        }

        Plugin reflectedOwner = findOwningPluginByReflection(cmd);
        if (reflectedOwner != null) {
            PluginLogger.debug("/{0} is registered through Paper's newer command API, "
                    + "had to fall back to reflection to find its owner", label);
            return new CommandOwner(reflectedOwner.getName(), reflectedOwner.getName().toLowerCase(), true);
        }

        String className = cmd.getClass().getSimpleName();
        PluginLogger.debug("/{0} isn't a plugin command and its real owner couldn't be found, "
                + "falling back to its wrapper class name: {1}", label, className);
        return new CommandOwner(className, className.toLowerCase(), true);
    }

    private static Plugin findOwningPluginByReflection(Command cmd) {
        for (String methodName : new String[] { "getPlugin", "getOwningPlugin", "plugin" }) {
            try {
                Method method = cmd.getClass().getMethod(methodName);
                Object result = method.invoke(cmd);
                if (result instanceof Plugin) return (Plugin) result;
            } catch (ReflectiveOperationException ignored) {}
        }

        for (Field field : cmd.getClass().getDeclaredFields()) {
            if (!Plugin.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(cmd);
                if (value instanceof Plugin) return (Plugin) value;
            } catch (ReflectiveOperationException ignored) {}
        }

        return null;
    }

    public int cacheSize() { return cache.size(); }

    public void pruneExpired() { cache.removeExpired(); }
    public boolean isMapLoaded() { return commandMap != null; }
}
