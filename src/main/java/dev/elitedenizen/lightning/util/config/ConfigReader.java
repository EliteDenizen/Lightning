package dev.elitedenizen.lightning.util.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ConfigReader {

    private ConfigReader() {}

    public static boolean has(Object obj, String key) {
        if (obj instanceof Map) return ((Map<?, ?>) obj).containsKey(key);
        if (obj instanceof ConfigurationSection) return ((ConfigurationSection) obj).contains(key);
        return false;
    }

    public static Object get(Object obj, String key) {
        if (obj instanceof Map) return ((Map<?, ?>) obj).get(key);
        if (obj instanceof ConfigurationSection) return ((ConfigurationSection) obj).get(key);
        return null;
    }

    public static void eachString(Object obj, Consumer<String> consumer) {
        if (obj instanceof List) {
            for (Object o : (List<?>) obj) {
                if (o != null) consumer.accept(o.toString());
            }
        } else if (obj != null) {
            consumer.accept(obj.toString());
        }
    }

    public static int asInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    public static long asLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    public static double asDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }

    public static boolean asBool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o == null) return false;
        String s = o.toString().trim().toLowerCase();
        return "true".equals(s) || "yes".equals(s) || "1".equals(s);
    }
}
