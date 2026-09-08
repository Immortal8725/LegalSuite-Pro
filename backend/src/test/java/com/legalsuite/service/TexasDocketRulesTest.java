package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TexasDocketRulesTest {
    @Test
    void personalInjuryIsTwoYearsFromAccrual() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.accrualDate = LocalDate.of(2024, 3, 1);
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals("personal_injury", r.track());
        assertEquals(LocalDate.of(2026, 3, 1), r.solDate());
        assertTrue(r.clocks().stream().anyMatch(c -> c.citation().contains("16.003")));
    }

    @Test
    void minorityTollsUntilEighteenThenTwoYears() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.accrualDate = LocalDate.of(2026, 9, 8);
        f.dateOfBirth = LocalDate.of(2010, 9, 8); // 16 years old
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals(LocalDate.of(2030, 9, 8), r.solDate());
        assertTrue(r.clocks().getFirst().ruleId().contains("minor"));
    }

    @Test
    void transitAuthorityAddsTtcaNotice() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Personal Injury";
        f.opposingParty = "Austin Metro Transit Authority";
        f.accrualDate = LocalDate.of(2026, 1, 1);
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertTrue(r.clocks().stream().anyMatch(c -> "notice".equals(c.kind()) && c.date().equals(LocalDate.of(2026, 7, 1))));
        assertEquals("notice", r.controlling().kind());
    }

    @Test
    void discoveryRuleMovesTheClock() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.caseType = "Personal Injury";
        f.accrualDate = LocalDate.of(2024, 1, 1);
        f.discoveryDate = LocalDate.of(2025, 6, 1);
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals(LocalDate.of(2027, 6, 1), r.solDate());
    }

    @Test
    void contractIsFourYears() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Contract";
        f.accrualDate = LocalDate.of(2022, 9, 8);
        assertEquals(LocalDate.of(2026, 9, 8), TexasDocketRules.compute(f).solDate());
    }

    @Test
    void estateCreditorWindowIsFourMonthsFromLetters() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Estate Planning";
        f.probateOpened = LocalDate.of(2026, 5, 8);
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals("estate", r.track());
        LocalDate creditor = r.clocks().stream().filter(c -> "notice".equals(c.kind())).findFirst().orElseThrow().date();
        assertEquals(LocalDate.of(2026, 9, 8), creditor);
        assertEquals(LocalDate.of(2028, 5, 8), r.solDate());
    }

    @Test
    void medMalMinorUsesFourteenthBirthday() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Medical malpractice";
        f.accrualDate = LocalDate.of(2026, 9, 8);
        f.dateOfBirth = LocalDate.of(2016, 9, 8); // age 10
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals(LocalDate.of(2030, 9, 8), r.solDate());
        assertTrue(r.clocks().stream().anyMatch(c -> "repose".equals(c.kind()) && c.date().equals(LocalDate.of(2036, 9, 8))));
    }

    @Test
    void injuryBeatsCommercialWording() {
        TexasDocketRules.Facts f = new TexasDocketRules.Facts();
        f.practiceArea = "Litigation";
        f.description = "Personal injury suit after a commercial vehicle collision.";
        f.accrualDate = LocalDate.of(2024, 9, 24);
        TexasDocketRules.Result r = TexasDocketRules.compute(f);
        assertEquals("personal_injury", r.track());
        assertEquals(LocalDate.of(2026, 9, 24), r.solDate());
    }

    @Test
    void yesterdayInNarrativeIsAccrual() {
        assertEquals(LocalDate.now().minusDays(1), TexasDocketRules.inferAccrual("Crash yesterday on I-35", LocalDate.now()));
    }
}

class ConflictEngineTest {
    @Test
    void lastNameHitsRelatedClient() {
        List<ConflictEngine.Party> parties = List.of(
                new ConflictEngine.Party("client", "Marcus Davis", "marcus.davis@example.com", "Davis", "Marcus", null, "C-1045", "Davis v. Metro Transit"));
        var hits = ConflictEngine.search(List.of("Jordan Davis"), parties);
        assertEquals(1, hits.size());
        assertEquals("related", hits.getFirst().get("role"));
    }

    @Test
    void metroTransitHitsAdverseEntity() {
        List<ConflictEngine.Party> parties = List.of(
                new ConflictEngine.Party("adverse", "Austin Metro Transit Authority", null, null, null,
                        "Austin Metro Transit Authority", "C-1045", "Davis v. Metro Transit"));
        var hits = ConflictEngine.search(List.of("Metro Transit"), parties);
        assertFalse(hits.isEmpty());
        assertEquals("adverse", hits.getFirst().get("role"));
    }

    @Test
    void priyaDoesNotMatchDavis() {
        List<ConflictEngine.Party> parties = List.of(
                new ConflictEngine.Party("client", "Marcus Davis", null, "Davis", "Marcus", null, null, null));
        assertTrue(ConflictEngine.search(List.of("Priya Nair"), parties).isEmpty());
    }

    @Test
    void counselMatchesOpposingLawyer() {
        List<ConflictEngine.Party> parties = List.of(
                new ConflictEngine.Party("counsel", "Renee Hale", "rhale@halewhit.com", "Hale", "Renee", "Hale & Whit", null, null));
        var hits = ConflictEngine.search(List.of("Renee Hale"), parties);
        assertEquals(1, hits.size());
        assertEquals("counsel", hits.getFirst().get("role"));
    }
}
