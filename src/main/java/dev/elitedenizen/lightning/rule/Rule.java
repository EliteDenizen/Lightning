package dev.elitedenizen.lightning.rule;

import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.action.ActionSet;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Rule {

    private final String ownerPlugin;
    private final String commandPattern;
    private final Pattern compiled;
    private final ActionSet actions;
    private final Set<String> exclude;
    private final Set<String> include;

    private Rule(Builder b) {
        this.ownerPlugin = b.ownerPlugin != null ? b.ownerPlugin.trim() : "";
        this.commandPattern = b.commandPattern != null ? b.commandPattern.trim() : "";
        this.actions = b.actions != null ? b.actions : ActionSet.empty();
        this.exclude = Collections.unmodifiableSet(new HashSet<>(b.exclude));
        this.include = Collections.unmodifiableSet(new HashSet<>(b.include));

        Pattern p = null;
        if (!this.commandPattern.isEmpty()) {
            try {
                String regex = this.commandPattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("{args}", ".*");
                p = Pattern.compile("^" + regex + "(\\s+.*)?$", Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                PluginLogger.warn("Invalid rule pattern '{0}': {1}", this.commandPattern, e.getMessage());
            }
        }
        this.compiled = p;
    }

    public boolean matches(String fullCommand, String resolvedPlugin) {
        if (!ownerPlugin.isEmpty() && !ownerPlugin.equalsIgnoreCase(resolvedPlugin)) return false;

        String label = fullCommand.trim().split("\\s+")[0].toLowerCase();
        if (!exclude.isEmpty() && exclude.contains(label)) return false;
        if (!include.isEmpty() && !include.contains(label)) return false;

        if (compiled == null) return true;
        return compiled.matcher(fullCommand.trim()).matches();
    }

    public void execute(ActionContext context) {
        if (actions.isEmpty()) return;
        actions.execute(context);
    }

    public String getOwnerPlugin() { return ownerPlugin; }
    public String getCommandPattern() { return commandPattern; }
    public ActionSet getActions() { return actions; }
    public boolean isEmpty() { return actions.isEmpty(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String ownerPlugin;
        private String commandPattern;
        private ActionSet actions;
        private Set<String> exclude = Collections.emptySet();
        private Set<String> include = Collections.emptySet();

        private Builder() {}

        public Builder plugin(String p) { this.ownerPlugin = p; return this; }
        public Builder pattern(String p) { this.commandPattern = p; return this; }
        public Builder actions(ActionSet a) { this.actions = a; return this; }
        public Builder exclude(Set<String> labels) { this.exclude = labels != null ? labels : Collections.emptySet(); return this; }
        public Builder include(Set<String> labels) { this.include = labels != null ? labels : Collections.emptySet(); return this; }

        public Rule build() { return new Rule(this); }
    }
}
