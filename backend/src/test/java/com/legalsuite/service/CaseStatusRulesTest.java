package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.*;

import com.legalsuite.common.ApiException;
import org.junit.jupiter.api.Test;

class CaseStatusRulesTest {
    @Test
    void acceptsKnownValuesInAnyCase() {
        assertEquals("trial", CaseStatusRules.normalise(" Trial "));
        assertEquals("on_hold", CaseStatusRules.normalise("On hold"));
    }

    @Test
    void rejectsUnknownAndBlank() {
        assertThrows(ApiException.class, () -> CaseStatusRules.normalise("banana"));
        assertThrows(ApiException.class, () -> CaseStatusRules.normalise(""));
        assertThrows(ApiException.class, () -> CaseStatusRules.normalise(null));
    }

    @Test
    void finishedMattersOnlyReopenToOpen() {
        assertThrows(ApiException.class, () -> CaseStatusRules.checkTransition("closed", "discovery"));
        assertThrows(ApiException.class, () -> CaseStatusRules.checkTransition("settled", "trial"));
        assertDoesNotThrow(() -> CaseStatusRules.checkTransition("closed", "open"));
        assertDoesNotThrow(() -> CaseStatusRules.checkTransition("settled", "closed"));
        assertDoesNotThrow(() -> CaseStatusRules.checkTransition("closed", "archived"));
        assertDoesNotThrow(() -> CaseStatusRules.checkTransition("trial", "intake"));
    }

    @Test
    void importsFallBackSafely() {
        assertEquals("open", CaseStatusRules.lenient("Active"));
        assertEquals("closed", CaseStatusRules.lenient("Won"));
        assertEquals("open", CaseStatusRules.lenient("weird"));
    }
}
