package dev.elitedenizen.lightning.action;

import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TemplateManager {

    private final Map<String, List<Action>> templates = new ConcurrentHashMap<>();

    public void loadFromFolder(File templatesFolder) {
        templates.clear();

        if (!templatesFolder.exists() || !templatesFolder.isDirectory()) {
            PluginLogger.debug("The templates folder does not exist yet, nothing to load");
            return;
        }

        List<File> files = new ArrayList<>();
        collectYamlFiles(templatesFolder, files);

        if (files.isEmpty()) {
            PluginLogger.debug("No yml files found anywhere under templates/");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            loaded += loadFile(file);
        }

        PluginLogger.info("Picked up {0} template(s) spread across {1} file(s) in templates/", loaded, files.size());
    }

    private void collectYamlFiles(File folder, List<File> out) {
        File[] entries = folder.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectYamlFiles(entry, out);
            } else if (entry.getName().endsWith(".yml")) {
                out.add(entry);
            }
        }
    }

    private int loadFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int count = 0;

        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;

            List<Action> actions = ActionLoader.loadActions(sec);
            if (actions.isEmpty()) {
                PluginLogger.warn("The template named '{0}' inside {1} has no usable actions in its list, "
                        + "so it will not do anything when referenced", key, file.getName());
                continue;
            }

            if (templates.containsKey(key.toLowerCase())) {
                PluginLogger.warn("Two templates are both named '{0}', the one in {1} is being used now",
                        key, file.getName());
            }

            templates.put(key.toLowerCase(), Collections.unmodifiableList(actions));
            PluginLogger.debug("Registered template '{0}' from {1} with {2} action(s)",
                    key, file.getName(), actions.size());
            count++;
        }

        return count;
    }

    public List<Action> get(String name) {
        if (name == null || name.isEmpty()) return Collections.emptyList();
        List<Action> found = templates.get(name.toLowerCase());
        if (found == null) return Collections.emptyList();
        return new ArrayList<>(found);
    }

    public boolean has(String name) {
        return name != null && templates.containsKey(name.toLowerCase());
    }

    public Set<String> names() {
        return new HashSet<>(templates.keySet());
    }

    public int count() {
        return templates.size();
    }

    public void clear() {
        templates.clear();
    }
}
