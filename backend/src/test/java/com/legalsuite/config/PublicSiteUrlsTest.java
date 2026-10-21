package com.legalsuite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PublicSiteUrlsTest {
    @AfterEach
    void reset() {
        PublicSiteUrls.setRoot("");
    }

    @Test
    void subdomainWhenRootSetElsePath() {
        PublicSiteUrls.setRoot("");
        assertEquals("/firm/ndlovu", PublicSiteUrls.of("ndlovu"));
        PublicSiteUrls.setRoot("https://Owlaw.co.za/");
        assertEquals("https://ndlovu.owlaw.co.za", PublicSiteUrls.of("ndlovu"));
        assertEquals("", PublicSiteUrls.clean("*.owlaw.co.za"));
        assertEquals("", PublicSiteUrls.clean("owlaw.co.za evil"));
    }

    @Test
    void slugsAreDnsLabelsAndNeverReserved() {
        String longName = "a".repeat(80);
        assertTrue(AuthService.slugify(longName).length() <= 63);
        assertEquals("ndlovu-partners", AuthService.slugify("Ndlovu & Partners"));
        assertFalse(AuthService.slugify("x".repeat(62) + " -y").endsWith("-"));
        assertTrue(AuthService.validSlug("ndlovu-partners"));
        assertFalse(AuthService.validSlug("www"));
        assertFalse(AuthService.validSlug("-bad"));
        assertFalse(AuthService.validSlug("Bad"));
        assertFalse(AuthService.validSlug("a".repeat(64)));
    }
}
