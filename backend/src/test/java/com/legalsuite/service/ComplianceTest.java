package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.common.ApiException;
import com.legalsuite.domain.Tenant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ComplianceTest {
    @Test
    void ffcCurrentRequiresNumberAndUnexpiredDate() {
        Tenant t = new Tenant();
        assertFalse(Compliance.ffcCurrent(t));
        t.setFfcNumber("FFC-GP-2026-44821");
        assertFalse(Compliance.ffcCurrent(t));
        t.setFfcExpiresOn(LocalDate.now().minusDays(1));
        assertFalse(Compliance.ffcCurrent(t));
        t.setFfcExpiresOn(LocalDate.now());
        assertTrue(Compliance.ffcCurrent(t));
    }

    @Test
    void zaTrustBlockedWithoutFfc() {
        Tenant t = new Tenant();
        t.setCountry("ZA");
        t.setState("GP");
        assertThrows(ApiException.class, () -> Compliance.requireFfcForTrust(t));
        t.setFfcNumber("FFC-1");
        t.setFfcExpiresOn(LocalDate.now().plusYears(1));
        Compliance.requireFfcForTrust(t);
    }

    @Test
    void popiaReadyNeedsOfficerManualAndOperator() {
        Tenant t = new Tenant();
        t.setInformationOfficerName("Thabo Ndlovu");
        t.setInformationOfficerEmail("thabo@ndlovulaw.co.za");
        t.setPaiaManualBody(PaiaManual.generate(t));
        assertFalse(Compliance.popiaReady(t));
        t.setPopiaOperatorAcknowledged(true);
        assertTrue(Compliance.popiaReady(t));
    }
}
