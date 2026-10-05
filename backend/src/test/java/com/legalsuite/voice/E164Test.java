package com.legalsuite.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.legalsuite.common.ApiException;
import org.junit.jupiter.api.Test;

class E164Test {
    @Test
    void southAfricaLocalBecomesE164() {
        assertEquals("+27825550199", E164.normalize("082 555 0199", "ZA"));
        assertEquals("+27115550100", E164.normalize("011 555 0100", "South Africa"));
    }

    @Test
    void unitedStatesLocalBecomesE164() {
        assertEquals("+14155550100", E164.normalize("(415) 555-0100", "US"));
    }

    @Test
    void explicitPlusIsKept() {
        assertEquals("+442079460958", E164.normalize("+44 20 7946 0958", "ZA"));
    }

    @Test
    void incompleteNumbersAreRejected() {
        assertThrows(ApiException.class, () -> E164.normalize("555", "US"));
    }
}
