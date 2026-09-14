package dev.elitedenizen.lightning.command;

import dev.elitedenizen.lightning.action.ActionSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CustomCommand {

    public enum UsesScope { PLAYER, GLOBAL }

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final String permissionMessage;
    private final String permissionDefault;
    private final String usage;
    private final String description;
    private final boolean playerOnly;
    private final boolean consoleOnly;
    private final boolean tabComplete;
    private final boolean visible;

    private final long cooldownMs;
    private final String cooldownMessage;
    private final String cooldownBypassPermission;
    private final String cooldownGroup;

    private final int usesLimit;
    private final UsesScope usesScope;
    private final String usesBypassPermission;
    private final String usesMessage;
    private final long usesResetMs;

    private final List<String> allowedWorlds;
    private final String worldDeniedMessage;

    private final boolean logUsage;
    private final boolean override;
    private final String sourceFile;

    private final List<CommandArgument> arguments;
    private final ActionSet actions;

    private CustomCommand(Builder b) {
        this.name = b.name;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(b.aliases));
        this.permission = b.permission != null ? b.permission : "";
        this.permissionMessage = b.permissionMessage != null ? b.permissionMessage : "";
        this.permissionDefault = b.permissionDefault != null ? b.permissionDefault : "false";
        this.usage = b.usage != null ? b.usage : "/" + b.name;
        this.description = b.description != null ? b.description : "";
        this.playerOnly = b.playerOnly;
        this.consoleOnly = b.consoleOnly;
        this.tabComplete = b.tabComplete;
        this.visible = b.visible;
        this.cooldownMs = Math.max(0, b.cooldownMs);
        this.cooldownMessage = b.cooldownMessage != null ? b.cooldownMessage : "";
        this.cooldownBypassPermission = b.cooldownBypassPermission != null ? b.cooldownBypassPermission : "";
        this.cooldownGroup = (b.cooldownGroup != null && !b.cooldownGroup.trim().isEmpty())
                ? b.cooldownGroup.trim().toLowerCase() : b.name;
        this.usesLimit = b.usesLimit;
        this.usesScope = b.usesScope != null ? b.usesScope : UsesScope.PLAYER;
        this.usesBypassPermission = b.usesBypassPermission != null ? b.usesBypassPermission : "";
        this.usesMessage = b.usesMessage != null ? b.usesMessage : "";
        this.usesResetMs = Math.max(0, b.usesResetMs);
        this.allowedWorlds = Collections.unmodifiableList(new ArrayList<>(b.allowedWorlds));
        this.worldDeniedMessage = b.worldDeniedMessage != null ? b.worldDeniedMessage : "";
        this.logUsage = b.logUsage;
        this.override = b.override;
        this.sourceFile = b.sourceFile != null ? b.sourceFile : "";
        this.arguments = Collections.unmodifiableList(new ArrayList<>(b.arguments));
        this.actions = b.actions != null ? b.actions : ActionSet.empty();
    }

    public String getName() { return name; }
    public List<String> getAliases() { return aliases; }
    public String getPermission() { return permission; }
    public String getPermissionMessage() { return permissionMessage; }
    public String getPermissionDefault() { return permissionDefault; }
    public String getUsage() { return usage; }
    public String getDescription() { return description; }
    public boolean isPlayerOnly() { return playerOnly; }
    public boolean isConsoleOnly() { return consoleOnly; }
    public boolean allowsTabComplete() { return tabComplete; }
    public boolean isVisible() { return visible; }
    public long getCooldownMs() { return cooldownMs; }
    public String getCooldownMessage() { return cooldownMessage; }
    public String getCooldownBypassPermission() { return cooldownBypassPermission; }
    public String getCooldownGroup() { return cooldownGroup; }
    public boolean hasCooldown() { return cooldownMs > 0; }
    public boolean hasUsesLimit() { return usesLimit > 0; }
    public int getUsesLimit() { return usesLimit; }
    public UsesScope getUsesScope() { return usesScope; }
    public String getUsesBypassPermission() { return usesBypassPermission; }
    public String getUsesMessage() { return usesMessage; }
    public long getUsesResetMs() { return usesResetMs; }
    public boolean hasUsesReset() { return usesResetMs > 0; }
    public List<String> getAllowedWorlds() { return allowedWorlds; }
    public String getWorldDeniedMessage() { return worldDeniedMessage; }
    public boolean hasWorldRestriction() { return !allowedWorlds.isEmpty(); }
    public boolean shouldLogUsage() { return logUsage; }
    public boolean shouldOverride() { return override; }
    public String getSourceFile() { return sourceFile; }
    public List<CommandArgument> getArguments() { return arguments; }
    public ActionSet getActions() { return actions; }

    public List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.addAll(aliases);
        return names;
    }

    public boolean isWorldAllowed(String worldName) {
        if (allowedWorlds.isEmpty()) return true;
        for (String w : allowedWorlds) {
            if (w.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private final List<String> allowedWorlds = new ArrayList<>();
        private String permission;
        private String permissionMessage;
        private String permissionDefault;
        private String usage;
        private String description;
        private boolean playerOnly = false;
        private boolean consoleOnly = false;
        private boolean tabComplete = true;
        private boolean visible = true;
        private long cooldownMs = 0;
        private String cooldownMessage;
        private String cooldownBypassPermission;
        private String cooldownGroup;
        private int usesLimit = -1;
        private UsesScope usesScope = UsesScope.PLAYER;
        private String usesBypassPermission;
        private String usesMessage;
        private long usesResetMs = 0;
        private String worldDeniedMessage;
        private boolean logUsage = false;
        private boolean override = false;
        private String sourceFile;
        private final List<CommandArgument> arguments = new ArrayList<>();
        private ActionSet actions;

        private Builder(String name) {
            this.name = name.toLowerCase().trim();
        }

        public Builder aliases(List<String> a) { if (a != null) aliases.addAll(a); return this; }
        public Builder permission(String p) { this.permission = p; return this; }
        public Builder permissionMessage(String p) { this.permissionMessage = p; return this; }
        public Builder permissionDefault(String d) { this.permissionDefault = d; return this; }
        public Builder usage(String u) { this.usage = u; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public Builder playerOnly(boolean p) { this.playerOnly = p; return this; }
        public Builder consoleOnly(boolean c) { this.consoleOnly = c; return this; }
        public Builder tabComplete(boolean t) { this.tabComplete = t; return this; }
        public Builder visible(boolean v) { this.visible = v; return this; }
        public Builder cooldownMs(long ms) { this.cooldownMs = ms; return this; }
        public Builder cooldownMessage(String m) { this.cooldownMessage = m; return this; }
        public Builder cooldownBypass(String p) { this.cooldownBypassPermission = p; return this; }
        public Builder cooldownGroup(String g) { this.cooldownGroup = g; return this; }
        public Builder usesLimit(int n) { this.usesLimit = n; return this; }
        public Builder usesScope(UsesScope s) { this.usesScope = s; return this; }
        public Builder usesBypass(String p) { this.usesBypassPermission = p; return this; }
        public Builder usesMessage(String m) { this.usesMessage = m; return this; }
        public Builder usesResetMs(long ms) { this.usesResetMs = ms; return this; }
        public Builder worlds(List<String> w) { if (w != null) allowedWorlds.addAll(w); return this; }
        public Builder worldDeniedMessage(String m) { this.worldDeniedMessage = m; return this; }
        public Builder logUsage(boolean l) { this.logUsage = l; return this; }
        public Builder override(boolean o) { this.override = o; return this; }
        public Builder sourceFile(String f) { this.sourceFile = f; return this; }
        public Builder arguments(List<CommandArgument> a) { if (a != null) arguments.addAll(a); return this; }
        public Builder actions(ActionSet a) { this.actions = a; return this; }

        public CustomCommand build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("A command entry is missing its name");
            }
            return new CustomCommand(this);
        }
    }
}
