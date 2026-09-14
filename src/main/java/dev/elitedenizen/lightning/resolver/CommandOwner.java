package dev.elitedenizen.lightning.resolver;

public final class CommandOwner {

    public static final CommandOwner UNKNOWN = new CommandOwner("", "", false);

    private final String pluginName;
    private final String namespace;
    private final boolean found;

    public CommandOwner(String pluginName, String namespace, boolean found) {
        this.pluginName = pluginName != null ? pluginName : "";
        this.namespace = namespace != null ? namespace : "";
        this.found = found;
    }

    public String getPluginName() { return pluginName; }
    public String getNamespace() { return namespace; }

    public boolean isFound() {
        return found;
    }

    public boolean isUnknown() {
        return !found;
    }

    @Override
    public String toString() {
        return found ? pluginName + " (" + namespace + ")" : "UNKNOWN";
    }
}
