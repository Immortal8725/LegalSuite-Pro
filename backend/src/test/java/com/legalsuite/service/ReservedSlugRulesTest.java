package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.*;

import com.legalsuite.common.ApiException;
import com.legalsuite.directory.DirectoryCatalog;
import org.junit.jupiter.api.Test;

class ReservedSlugRulesTest {
    @Test
    void everyPracticeAreaIsReserved() {
        for (DirectoryCatalog.Option a : DirectoryCatalog.PRACTICE_AREAS) {
            assertFalse(AuthService.validSlug(a.slug()), a.slug());
        }
    }

    @Test
    void systemWordsAreReserved() {
        for (String s : new String[] {"www", "api", "admin", "app", "find-a-lawyer", "portal"}) assertFalse(AuthService.validSlug(s), s);
    }

    @Test
    void citiesAndNormalNamesAreAllowed() {
        assertTrue(AuthService.validSlug("pretoria"));
        assertTrue(AuthService.validSlug("ndlovu-partners"));
        assertTrue(AuthService.validSlug("criminal-law"));
    }

    @Test
    void typedReservedSlugIsRejectedWithClearMessage() {
        ApiException e = assertThrows(ApiException.class, () -> AuthService.requireAllowedSlug("Criminal"));
        assertEquals(AuthService.RESERVED_MESSAGE, e.getMessage());
        assertDoesNotThrow(() -> AuthService.requireAllowedSlug("Mokoena Attorneys"));
    }

    @Test
    void nameDerivedSlugIsSuffixed() {
        assertEquals("tax-law", AuthService.newFirmSlug("Tax"));
        assertEquals("admin-law", AuthService.newFirmSlug("Admin"));
    }
}
