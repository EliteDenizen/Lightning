package com.denizenmc.lightning.tool;

import com.denizenmc.lightning.Lightning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// Locates plugin directories and collects supported configuration and text files.
final class ConfigFiles {

    private static final long MAX_SIZE = 2_000_000L;

    private ConfigFiles() {
    }

    // Returns plugin directories, optionally filtered by name.
    static @NotNull List<Path> folders(@NotNull final Lightning plugin, @Nullable final String only) {
        File root = plugin.getDataFolder().getParentFile();
        File[] folders = root.listFiles(File::isDirectory);
        List<Path> result = new ArrayList<>();
        if (folders == null) return result;
        for (File folder : folders) {
            if (only == null || folder.getName().equalsIgnoreCase(only)) result.add(folder.toPath());
        }
        return result;
    }

    // Recursively collects supported text files in sorted order.
    static @NotNull List<Path> texts(@NotNull final Path folder) {
        try (Stream<Path> walk = java.nio.file.Files.walk(folder)) {
            return walk.filter(java.nio.file.Files::isRegularFile)
                    .filter(ConfigFiles::readable)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean readable(final Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties") || name.endsWith(".txt"))) return false;
        try {
            return java.nio.file.Files.size(path) < MAX_SIZE;
        } catch (IOException e) {
            return false;
        }
    }
}
