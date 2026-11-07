package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PublicContentCacheTest {
    @Test
    void firmDeleteDropsTheSiteAndTheDirectory() {
        PublicContentCache cache = new PublicContentCache();
        cache.get("site:ndlovu", () -> "old-site");
        cache.get("directory:firm:ndlovu", () -> "old-card");
        cache.get("blog:post:raf", () -> "post");
        cache.evictFirm("Ndlovu");
        assertEquals("fresh-site", cache.get("site:ndlovu", () -> "fresh-site"));
        assertEquals("fresh-card", cache.get("directory:firm:ndlovu", () -> "fresh-card"));
        assertEquals("post", cache.get("blog:post:raf", () -> "replaced"));
    }

    @Test
    void publishDropsBlogEntries() {
        PublicContentCache cache = new PublicContentCache();
        cache.get("blog:list::1", () -> "list");
        cache.evictBlog();
        assertEquals("new-list", cache.get("blog:list::1", () -> "new-list"));
    }

    @Test
    void photoVersionDecidesHowLongItMayBeCached() {
        assertEquals(Duration.ofDays(30), PublicSitePhotoService.liveCacheAge(9L, 9L));
        assertEquals(Duration.ofSeconds(60), PublicSitePhotoService.liveCacheAge(null, 9L));
        assertEquals(Duration.ofSeconds(60), PublicSitePhotoService.liveCacheAge(1L, 9L));
    }
}
