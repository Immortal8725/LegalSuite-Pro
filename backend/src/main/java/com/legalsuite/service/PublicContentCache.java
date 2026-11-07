package com.legalsuite.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * In-process cache for public, non-personal reads. One free JVM, no Redis.
 * Keys are path plus slug. Nothing here is client, matter, trust or FICA data.
 * Entries expire quickly, and publish or delete drops them at once.
 */
@Component
public class PublicContentCache {
    public static final String PUBLIC_CACHE = "public, max-age=60, stale-while-revalidate=300";

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> load) {
        Object hit = cache.getIfPresent(key);
        if (hit != null) return (T) hit;
        T value = load.get();
        if (value != null) cache.put(key, value);
        return value;
    }

    public void evictSite(String slug) {
        if (slug == null || slug.isBlank()) return;
        cache.invalidate("site:" + slug.trim().toLowerCase());
    }

    /** A firm left or returned to the public site and the directory. */
    public void evictFirm(String slug) {
        evictSite(slug);
        evictDirectory();
    }

    public void evictBlog() {
        evictPrefix("blog:");
    }

    public void evictDirectory() {
        evictPrefix("directory:");
    }

    public void evictPlans() {
        cache.invalidate("plans");
        cache.invalidate("modules");
        cache.invalidate("litigation:tracks");
    }

    private void evictPrefix(String prefix) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}
