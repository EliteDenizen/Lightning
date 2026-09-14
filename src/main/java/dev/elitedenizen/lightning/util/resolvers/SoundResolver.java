package dev.elitedenizen.lightning.util.resolvers;

import org.bukkit.Sound;

import java.util.Locale;

public final class SoundResolver {

    private SoundResolver() {}

    public static Sound resolve(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String name = input.trim().toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
