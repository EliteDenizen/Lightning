package dev.elitedenizen.lightning.util.text;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;

public final class PlaceholderEngine {

    private static Method papiSetPlaceholders;
    private static boolean papiAvailable;

    static {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiSetPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
            papiAvailable = true;
        } catch (Exception ignored) {
            papiAvailable = false;
        }
    }

    private PlaceholderEngine() {}

    public static boolean isPapiAvailable() { return papiAvailable; }

    public static String apply(String text, Player player, Map<String, String> extras) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        if (player != null) {
            result = result
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()))
                .replace("{health}", String.valueOf((int) player.getHealth()))
                .replace("{food}", String.valueOf(player.getFoodLevel()))
                .replace("{level}", String.valueOf(player.getLevel()));
        }

        if (extras != null && !extras.isEmpty()) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                result = result.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
            }
        }

        if (papiAvailable && papiSetPlaceholders != null && player != null && player.isOnline()) {
            try {
                result = (String) papiSetPlaceholders.invoke(null, player, result);
            } catch (Exception ignored) {}
        }

        return result;
    }

    public static String apply(String text, Player player) {
        return apply(text, player, null);
    }
}
