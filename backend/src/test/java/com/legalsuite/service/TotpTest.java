package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {
    @Test
    void codeAtRoundTripVerifies() {
        String secret = Totp.newSecret();
        long step = Instant.now().getEpochSecond() / 30;
        String code = Totp.codeAt(secret, step);
        assertEquals(6, code.length());
        assertTrue(Totp.verify(secret, code));
        assertFalse(Totp.verify(secret, "000000"));
        assertFalse(Totp.verify(secret, "abc"));
    }
}
