package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.domain.LegalCase;
import org.junit.jupiter.api.Test;

class DocketHoldTest {
    @Test
    void overdueNoticeHoldsUntilItIsServed() {
        LegalCase matter = matter("[{\"kind\":\"notice\",\"date\":\"2020-01-01\",\"citation\":\"Act 40 of 2002 s 3\"}]");
        assertTrue(PracticeService.docketHold(matter));
        assertTrue(PracticeService.docketHoldReason(matter).contains("Act 40 of 2002 s 3"));
        matter.setNoticeServed(true);
        assertFalse(PracticeService.docketHold(matter));
    }

    @Test
    void lodgedRafClaimReleasesTheLodgeHold() {
        LegalCase matter = matter("[{\"kind\":\"raf_lodge\",\"date\":\"2020-01-01\",\"citation\":\"RAF Act s 23\"}]");
        assertTrue(PracticeService.docketHold(matter));
        matter.setRafClaimLodged(true);
        assertFalse(PracticeService.docketHold(matter));
    }

    @Test
    void futureClockDoesNotHold() {
        LegalCase matter = matter("[{\"kind\":\"ccma\",\"date\":\"2099-01-01\",\"citation\":\"LRA s 191\"}]");
        assertFalse(PracticeService.docketHold(matter));
    }

    private static LegalCase matter(String clocks) {
        LegalCase matter = new LegalCase();
        matter.setDocketClocksJson(clocks);
        return matter;
    }
}
