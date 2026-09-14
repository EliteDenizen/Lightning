package dev.elitedenizen.lightning.util.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ExpiringCache<K, V> {

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;

    private ExpiringCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public static <K, V> ExpiringCache<K, V> withLifetime(long amount, TimeUnit unit) {
        return new ExpiringCache<>(unit.toMillis(amount));
    }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) return null;
        if (isExpired(entry)) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    public V getOrCompute(K key, Function<K, V> supplier) {
        V existing = get(key);
        if (existing != null) return existing;

        V computed = supplier.apply(key);
        if (computed != null) put(key, computed);
        return computed;
    }

    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, System.currentTimeMillis()));
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public void removeExpired() {
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue())) iterator.remove();
        }
    }

    private boolean isExpired(Entry<V> entry) {
        return System.currentTimeMillis() - entry.storedAt > ttlMillis;
    }

    private static final class Entry<V> {
        final V value;
        final long storedAt;

        Entry(V value, long storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }
    }
}
