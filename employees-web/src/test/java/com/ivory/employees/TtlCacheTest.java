package com.ivory.employees;

import com.ivory.employees.cache.TtlCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TtlCacheTest {

    private final AtomicLong clock = new AtomicLong(0);

    private TtlCache<Long, String> cacheWithTwoHourTtl() {
        return new TtlCache<>(Duration.ofHours(2), clock::get);
    }

    @Test
    void servesEntriesUntilTheRetentionTimeElapses() {
        TtlCache<Long, String> cache = cacheWithTwoHourTtl();
        cache.put(1001L, "Avi Cohen");

        clock.set(Duration.ofMinutes(119).toMillis());
        assertEquals("Avi Cohen", cache.get(1001L));

        clock.set(Duration.ofHours(2).toMillis());
        assertNull(cache.get(1001L), "the entry must expire exactly at the retention time");
        assertEquals(0, cache.size());
    }

    @Test
    void loaderRunsOnlyOnCacheMisses() {
        TtlCache<Long, String> cache = cacheWithTwoHourTtl();
        AtomicLong loads = new AtomicLong();

        for (int i = 0; i < 3; i++) {
            assertEquals("loaded", cache.get(1001L, key -> {
                loads.incrementAndGet();
                return "loaded";
            }));
        }
        assertEquals(1, loads.get());

        clock.set(Duration.ofHours(2).toMillis() + 1);
        cache.get(1001L, key -> {
            loads.incrementAndGet();
            return "loaded";
        });
        assertEquals(2, loads.get(), "after expiry the value must be loaded again");
    }

    @Test
    void countsHitsAndMissesAndPurgesExpiredEntries() {
        TtlCache<Long, String> cache = cacheWithTwoHourTtl();
        cache.put(1L, "a");
        cache.put(2L, "b");

        cache.get(1L);
        cache.get(99L);
        assertEquals(1, cache.hits());
        assertEquals(1, cache.misses());

        clock.set(Duration.ofHours(3).toMillis());
        assertEquals(2, cache.purgeExpired());
        assertEquals(0, cache.size());
    }
}
