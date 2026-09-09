package com.ivory.employees.cache;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Small thread-safe cache with a fixed time-to-live per entry. Entries are evicted lazily on read
 * and, in addition, in bulk by {@link #purgeExpired()} (scheduled by the application).
 *
 * @param <K> key type
 * @param <V> cached value type
 */
public final class TtlCache<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final LongSupplier clock;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public TtlCache(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    /** Test seam: lets a test drive expiry without sleeping. */
    public TtlCache(Duration ttl, LongSupplier clock) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive but was " + ttl);
        }
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
    }

    /** Returns the cached value, or {@code null} when absent or expired. */
    public V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.isExpired(clock.getAsLong())) {
            entries.remove(key, entry);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.value();
    }

    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.getAsLong() + ttlMillis));
    }

    /**
     * Returns the cached value, or computes and caches it. The loader may return {@code null},
     * which is not cached.
     */
    public V get(K key, Function<K, V> loader) {
        V cached = get(key);
        if (cached != null) {
            return cached;
        }
        V loaded = loader.apply(key);
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    public void invalidate(K key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    /** Drops every expired entry; returns how many were removed. */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int before = entries.size();
        entries.values().removeIf(entry -> entry.isExpired(now));
        return before - entries.size();
    }

    public int size() {
        return entries.size();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public Duration ttl() {
        return Duration.ofMillis(ttlMillis);
    }

    private record Entry<V>(V value, long expiresAtMillis) {

        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }
}
