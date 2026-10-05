package com.legalsuite.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.common.ApiException;
import org.junit.jupiter.api.Test;

class EmergencyNumbersTest {
    @Test
    void shortCodesStayOnTheHandset() {
        for (String raw : new String[] {
                "911", "9-1-1", "112", "999", "000", "988", "10111", "10177", "+1911", "+2710111", "107"
        }) {
            assertTrue(EmergencyNumbers.isEmergency(raw), raw);
            ApiException ex = assertThrows(ApiException.class, () -> EmergencyNumbers.rejectIfEmergency(raw));
            assertTrue(ex.getMessage().toLowerCase().contains("device dialer"));
        }
    }

    @Test
    void ordinaryNumbersAreNotEmergency() {
        assertFalse(EmergencyNumbers.isEmergency("+14155550100"));
        assertFalse(EmergencyNumbers.isEmergency("+27115550100"));
        assertFalse(EmergencyNumbers.isEmergency("082 555 0199"));
        assertFalse(EmergencyNumbers.isEmergency("(415) 555-0199"));
    }
}
