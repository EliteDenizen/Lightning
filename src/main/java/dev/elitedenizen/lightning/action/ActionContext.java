package dev.elitedenizen.lightning.action;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.text.ArgumentParser;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import dev.elitedenizen.lightning.util.text.PlaceholderEngine;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ActionContext {

    private final Lightning plugin;
    private final Player player;
    private final String ownerPlugin;
    private final String commandLabel;
    private final String fullCommand;
    private final String[] args;
    private final ArgumentParser argParser;
    private final Map<String, String> variables;

    private ActionContext(Builder b) {
        this.plugin = b.plugin;
        this.player = b.player;
        this.ownerPlugin = b.ownerPlugin != null ? b.ownerPlugin : "";
        this.commandLabel = b.commandLabel != null ? b.commandLabel : "";
        this.fullCommand = b.fullCommand != null ? b.fullCommand : "";
        this.args = b.args != null ? b.args : new String[0];
        this.argParser = new ArgumentParser(this.args);
        this.variables = b.variables != null ? Collections.unmodifiableMap(new HashMap<>(b.variables)) : Collections.emptyMap();
    }

    public Lightning getPlugin() { return plugin; }
    public Player getPlayer() { return player; }
    public String getOwnerPlugin() { return ownerPlugin; }
    public String getCommandLabel() { return commandLabel; }
    public String getFullCommand() { return fullCommand; }
    public String[] getArgs() { return args; }
    public int getArgCount() { return args.length; }
    public ArgumentParser getArgParser(){ return argParser; }
    public Map<String, String> getVariables() { return variables; }

    public boolean isPlayerOnline() {
        return player != null && player.isOnline();
    }

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) return true;
        if (player == null) return true;
        if (!player.isOnline()) return false;
        return player.hasPermission(permission);
    }

    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) return true;
        for (String p : permissions) {
            if (hasPermission(p)) return true;
        }
        return false;
    }

    public boolean hasAllPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) return true;
        for (String p : permissions) {
            if (!hasPermission(p)) return false;
        }
        return true;
    }

    public String applyPlaceholders(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        result = result.replace("{command}", commandLabel);
        result = result.replace("{fullcommand}", fullCommand);
        result = result.replace("{plugin}", ownerPlugin);
        result = argParser.replace(result);
        result = PlaceholderEngine.apply(result, player, variables);
        return result;
    }

    public String colorize(String text) {
        return ColorFormatter.colorize(applyPlaceholders(text), player);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Lightning plugin;
        private Player player;
        private String ownerPlugin;
        private String commandLabel;
        private String fullCommand;
        private String[] args;
        private Map<String, String> variables;

        private Builder() {}

        public Builder plugin(Lightning p) { this.plugin = p;        return this; }
        public Builder player(Player p) { this.player = p;        return this; }
        public Builder ownerPlugin(String s) { this.ownerPlugin = s;   return this; }
        public Builder commandLabel(String s) { this.commandLabel = s;  return this; }
        public Builder fullCommand(String s) { this.fullCommand = s;   return this; }
        public Builder args(String[] a) { this.args = a;          return this; }
        public Builder variables(Map<String, String> v) { this.variables = v; return this; }

        public Builder variable(String key, String value) {
            if (this.variables == null) this.variables = new HashMap<>();
            this.variables.put(key, value);
            return this;
        }

        public ActionContext build() {
            if (plugin == null) throw new IllegalStateException("ActionContext requires a plugin instance");
            return new ActionContext(this);
        }
    }
}
