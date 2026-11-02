package com.legalsuite.litigation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourtDaysTest {
    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test
    void easterHolidaysAndSundayRule() {
        assertTrue(CourtDays.isPublicHoliday(d("2026-04-03")), "Good Friday 2026");
        assertTrue(CourtDays.isPublicHoliday(d("2026-04-06")), "Family Day 2026");
        assertTrue(CourtDays.isPublicHoliday(d("2027-03-26")), "Good Friday 2027");
        assertTrue(CourtDays.isPublicHoliday(d("2027-03-29")), "Family Day 2027");
        assertTrue(CourtDays.isPublicHoliday(d("2026-08-10")), "Women's Day on a Sunday moves to Monday");
        assertFalse(CourtDays.isPublicHoliday(d("2026-08-11")));
        assertTrue(CourtDays.isPublicHoliday(d("2026-06-16")));
        assertFalse(CourtDays.isCourtDay(d("2026-11-07"), true), "Saturday");
    }

    @Test
    void diesNonRunsSixteenDecemberToFifteenJanuary() {
        assertTrue(CourtDays.isDiesNon(d("2026-12-16")));
        assertTrue(CourtDays.isDiesNon(d("2027-01-15")));
        assertFalse(CourtDays.isDiesNon(d("2026-12-15")));
        assertFalse(CourtDays.isDiesNon(d("2027-01-16")));
        assertFalse(CourtDays.isCourtDay(d("2027-01-05"), true));
        assertTrue(CourtDays.isCourtDay(d("2027-01-05"), false));
    }

    @Test
    void countsCourtDaysExcludingTheFirstDay() {
        assertEquals(d("2026-11-16"), CourtDays.addCourtDays(d("2026-11-02"), 10, true));
        assertEquals(d("2026-11-30"), CourtDays.addCourtDays(d("2026-11-02"), 20, true));
        // Over the Easter long weekend 2026.
        assertEquals(d("2026-04-08"), CourtDays.addCourtDays(d("2026-04-01"), 3, true));
        // Across the dies non: 6 days in December, the rest from 18 January 2027.
        assertEquals(d("2027-01-21"), CourtDays.addCourtDays(d("2026-12-07"), 10, true));
        // Without the dies non only 16 Dec (Day of Reconciliation) is skipped.
        assertEquals(d("2026-12-22"), CourtDays.addCourtDays(d("2026-12-07"), 10, false));
    }

    @Test
    void calendarPeriodsRollPastSundaysAndHolidays() {
        // 14 days from 26 Nov 2026 is Thursday 10 Dec.
        assertEquals(d("2026-12-10"), CourtDays.due(d("2026-11-26"), 14, "calendar_days", false));
        // 30 days from 10 Nov 2026 is Thursday 10 Dec.
        assertEquals(d("2026-12-10"), CourtDays.due(d("2026-11-10"), 30, "calendar_days", false));
        // 14 days from 2 Dec 2026 is Wed 16 Dec, a holiday, so Thursday 17 Dec.
        assertEquals(d("2026-12-17"), CourtDays.due(d("2026-12-02"), 14, "calendar_days", false));
        // 6 weeks, months, years.
        assertEquals(d("2026-12-14"), CourtDays.due(d("2026-11-02"), 6, "weeks", false));
        assertEquals(d("2027-05-03"), CourtDays.due(d("2026-11-02"), 6, "months", false));
        assertThrows(IllegalArgumentException.class, () -> CourtDays.due(d("2026-11-02"), 1, "fortnights", false));
    }

    @Test
    void stageTransitions() {
        LitigationCatalog cat = new LitigationCatalog();
        assertEquals(10, cat.tracks().size());
        LitigationCatalog.Track hc = cat.track("high_court_action");
        assertNull(LitigationCatalog.transitionProblem(hc, List.of(), "letter_of_demand"));
        assertNull(LitigationCatalog.transitionProblem(hc, List.of(), "summons_issued"), "stages may be skipped");
        assertNotNull(LitigationCatalog.transitionProblem(hc, List.of("plea"), "summons_served"), "no going back");
        assertNotNull(LitigationCatalog.transitionProblem(hc, List.of("plea"), "plea"), "no repeat");
        assertNotNull(LitigationCatalog.transitionProblem(hc, List.of(), "banana"));
        assertNotNull(LitigationCatalog.transitionProblem(hc, List.of(), "default_judgment"), "side stage needs its main stage");
        assertNull(LitigationCatalog.transitionProblem(hc, List.of("summons_served"), "default_judgment"));
        assertNotNull(LitigationCatalog.transitionProblem(hc, List.of("summons_served", "withdrawn"), "plea"), "closed after a terminal stage");
        for (LitigationCatalog.Track t : cat.tracks()) {
            assertFalse(t.stages().isEmpty(), t.id());
            assertFalse(t.courtLevels().isEmpty(), t.id());
            for (LitigationCatalog.Rule r : t.rules()) {
                assertNotNull(t.stage(r.trigger()), r.id());
                assertFalse(r.source() == null || r.source().isBlank(), r.id());
                assertDoesNotThrow(() -> CourtDays.due(d("2026-11-02"), r.amount(), r.unit(), r.diesNon()), r.id());
            }
        }
    }
}
