package dev.steppedplayeranimations.timing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Render-thread cache for per-entity animation state. Entries that disappear from view are
 * discarded after a quiet period so long sessions and entity churn cannot grow the cache forever.
 */
public final class ExpiringStateCache<K, V> {
    private static final long RETENTION_NANOS = 60_000_000_000L;
    private static final long CLEANUP_INTERVAL_NANOS = 10_000_000_000L;

    private final Map<K, Entry<V>> entries = new HashMap<>();
    private long nextCleanupNanos;

    public V getOrCreate(K key, Supplier<V> factory) {
        long now = System.nanoTime();
        if (now >= nextCleanupNanos) {
            removeExpired(now);
            nextCleanupNanos = now + CLEANUP_INTERVAL_NANOS;
        }

        Entry<V> entry = entries.get(key);
        if (entry == null) {
            entry = new Entry<>(factory.get(), now);
            entries.put(key, entry);
        } else {
            entry.lastAccessNanos = now;
        }
        return entry.value;
    }

    private void removeExpired(long now) {
        Iterator<Entry<V>> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry<V> entry = iterator.next();
            if (now - entry.lastAccessNanos >= RETENTION_NANOS) {
                iterator.remove();
            }
        }
    }

    private static final class Entry<V> {
        private final V value;
        private long lastAccessNanos;

        private Entry(V value, long lastAccessNanos) {
            this.value = value;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
