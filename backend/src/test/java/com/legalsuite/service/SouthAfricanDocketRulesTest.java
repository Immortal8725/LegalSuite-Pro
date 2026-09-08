package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SouthAfricanDocketRulesTest {
    @Test
    void rafIdentifiedVehicleIsThreeYears() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "RAF";
        f.opposingParty = "Road Accident Fund";
        f.accrualDate = LocalDate.of(2023, 9, 8);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals("raf", r.track());
        assertEquals(LocalDate.of(2026, 9, 8), r.solDate());
        assertTrue(r.clocks().stream().anyMatch(c -> "raf_lodge".equals(c.kind()) && c.citation().contains("s 23")));
        assertFalse(r.clocks().stream().anyMatch(c -> "za.act40.s3".equals(c.ruleId())));
    }

    @Test
    void rafHitAndRunIsTwoYears() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.description = "Hit and run on the N1. Unidentified driver.";
        f.accrualDate = LocalDate.of(2024, 9, 8);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals(LocalDate.of(2026, 9, 8), r.solDate());
        assertTrue(r.clocks().getFirst().title().toLowerCase().contains("hit-and-run"));
    }

    @Test
    void rafLodgedAddsFiveYearSummons() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "RAF";
        f.opposingParty = "Road Accident Fund";
        f.accrualDate = LocalDate.of(2023, 9, 8);
        f.rafClaimLodged = true;
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertTrue(r.clocks().stream().anyMatch(c -> "raf_summons".equals(c.kind()) && c.date().equals(LocalDate.of(2028, 9, 8))));
    }

    @Test
    void rafMinorSuspendsUntilMajority() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "RAF";
        f.opposingParty = "Road Accident Fund";
        f.accrualDate = LocalDate.of(2026, 9, 8);
        f.dateOfBirth = LocalDate.of(2010, 9, 8);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals(LocalDate.of(2031, 9, 8), r.solDate());
    }

    @Test
    void cityOfJohannesburgAddsAct40Notice() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.opposingParty = "City of Johannesburg";
        f.accrualDate = LocalDate.of(2026, 1, 1);
        f.description = "Pothole wrecked the bakkie and injured the driver.";
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals("organ_of_state", r.track());
        assertTrue(r.clocks().stream().anyMatch(c -> "notice".equals(c.kind()) && c.date().equals(LocalDate.of(2026, 7, 1))));
        assertEquals("notice", r.controlling().kind());
        assertTrue(r.clocks().stream().anyMatch(c -> "sol".equals(c.kind()) && c.date().equals(LocalDate.of(2029, 1, 1))));
    }

    @Test
    void contractIsThreeYearsNotFour() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Contract";
        f.accrualDate = LocalDate.of(2023, 9, 8);
        assertEquals(LocalDate.of(2026, 9, 8), SouthAfricanDocketRules.compute(f).solDate());
    }

    @Test
    void unfairDismissalIsThirtyDays() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Labour";
        f.description = "Unfair dismissal from the warehouse.";
        f.accrualDate = LocalDate.of(2026, 9, 1);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals("labour", r.track());
        assertEquals(LocalDate.of(2026, 10, 1), r.solDate());
        assertTrue(r.clocks().getFirst().citation().contains("191"));
    }

    @Test
    void delictMinorUsesSection13() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.description = "Slip at a private shopping centre.";
        f.accrualDate = LocalDate.of(2026, 9, 8);
        f.dateOfBirth = LocalDate.of(2016, 9, 8);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals(LocalDate.of(2035, 9, 8), r.solDate());
    }

    @Test
    void knowledgeDateMovesPrescription() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Medical negligence";
        f.accrualDate = LocalDate.of(2022, 1, 1);
        f.discoveryDate = LocalDate.of(2024, 6, 1);
        TexasDocketRules.Result r = SouthAfricanDocketRules.compute(f);
        assertEquals(LocalDate.of(2027, 6, 1), r.solDate());
    }
}

class CallEthicsSouthAfricaTest {
    @Test
    void ricaIsOnePartyButStillOptIn() {
        Map<String, Object> r = CallEthics.forJurisdiction("ZA", "GP");
        assertFalse((Boolean) r.get("allPartyConsent"));
        assertTrue(String.valueOf(r.get("notice")).contains("RICA"));
        assertTrue(String.valueOf(r.get("notice")).contains("opt-in"));
    }

    @Test
    void gautengWithoutCountryStillRica() {
        Map<String, Object> r = CallEthics.forState("GP");
        assertEquals("ZA", r.get("country"));
    }
}

class DocketEngineTest {
    @Test
    void southAfricaTenantRoutesToZa() {
        com.legalsuite.domain.Tenant t = new com.legalsuite.domain.Tenant();
        t.setCountry("ZA");
        t.setState("GP");
        assertEquals("ZA", DocketEngine.of(t));
        assertEquals("ZAR", DocketEngine.currency(t));
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Contract";
        f.accrualDate = LocalDate.of(2023, 9, 8);
        assertEquals(LocalDate.of(2026, 9, 8), DocketEngine.compute(t, f).solDate());
    }
}

class ConflictEngineSouthAfricaTest {
    @Test
    void cityOfJohannesburgHitsAdverseEntity() {
        var parties = java.util.List.of(
                new ConflictEngine.Party("adverse", "City of Johannesburg Metropolitan Municipality", null, null, null,
                        "City of Johannesburg Metropolitan Municipality", "C-2002", "Van der Merwe v City of Johannesburg"));
        var hits = ConflictEngine.search(java.util.List.of("City of Johannesburg"), parties);
        assertFalse(hits.isEmpty());
        assertEquals("adverse", hits.getFirst().get("role"));
    }
}
