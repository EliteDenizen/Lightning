package com.denizenmc.lightning;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class Yaml {

    private Yaml() {
    }

    /**
     * Loads a YAML file containing Sparks, Rewires, or custom commands.
     *
     * <p>Bukkit normally interprets dots in configuration keys as path separators.
     * This would turn a key such as {@code ess.text} into a nested section instead
     * of preserving it as a single key. Using a null character as the path
     * separator ensures that keys are read exactly as written.</p>
     */
    public static @NotNull YamlConfiguration read(@NotNull final Path path, @NotNull final String file, @NotNull final Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('\u0000');
        try {
            yaml.load(path.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            logger.warning(file + " could not be loaded: " + e.getMessage());
        }
        return yaml;
    }
}
