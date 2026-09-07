package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CallEthicsTest {
    @Test
    void californiaRequiresAllParty() {
        Map<String, Object> r = CallEthics.forState("ca");
        assertEquals(true, r.get("allPartyConsent"));
        assertTrue(String.valueOf(r.get("notice")).toLowerCase().contains("all-party"));
    }

    @Test
    void texasIsOnePartyButStillOptIn() {
        Map<String, Object> r = CallEthics.forState("TX");
        assertFalse((Boolean) r.get("allPartyConsent"));
        assertTrue(String.valueOf(r.get("notice")).contains("opt-in"));
    }
}

class RetainSolTest {
    @Test
    void injuryGetsTwoYears() {
        assertEquals(LocalDate.now().plusYears(2), RetainService.defaultSol("Personal Injury"));
    }
}
