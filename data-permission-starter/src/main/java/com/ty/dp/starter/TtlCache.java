package com.ty.dp.starter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal TTL cache. The library avoids a hard dependency on Caffeine or
 * Spring Cache so it can drop into any RuoYi variant.
 */
final class TtlCache<V> {

    private final ConcurrentHashMap<String, Entry<V>> entries =
            new ConcurrentHashMap<String, Entry<V>>();
    private final long ttlMillis;

    TtlCache(long ttlSeconds) {
        this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
    }

    V get(String key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    void put(String key, V value) {
        entries.put(key, new Entry<V>(value, System.currentTimeMillis() + ttlMillis));
    }

    void clear() {
        entries.clear();
    }

    private static final class Entry<V> {
        private final V value;
        private final long expiresAt;

        private Entry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
