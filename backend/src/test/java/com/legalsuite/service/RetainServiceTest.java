package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.legalsuite.domain.Tenant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetainServiceTest {
    @Test
    void explicitAmountBeatsTheFirmAndTheAttorney() {
        Tenant firm = firm("{\"defaultRetainer\":\"9500\"}");
        assertEquals(new BigDecimal("1800"), RetainService.resolveRetainer(1800, firm, new BigDecimal("4200")));
    }

    @Test
    void firmDefaultBeatsTheAttorneyRate() {
        Tenant firm = firm("{\"retainerAmount\":\"95000\"}");
        assertEquals(new BigDecimal("95000"), RetainService.resolveRetainer(null, firm, new BigDecimal("4200")));
    }

    @Test
    void attorneyRateIsUsedWhenTheFirmHasNoDefault() {
        assertEquals(new BigDecimal("4200"), RetainService.resolveRetainer(null, new Tenant(), new BigDecimal("4200")));
        assertEquals(new BigDecimal("350"), RetainService.resolveRetainer("", firm(null), new BigDecimal("350")));
    }

    @Test
    void missingAmountDoesNotInventARetainer() {
        assertEquals(BigDecimal.ZERO, RetainService.resolveRetainer(null, new Tenant(), null));
        assertEquals(BigDecimal.ZERO, RetainService.resolveRetainer(null, firm("{}"), BigDecimal.ZERO));
    }

    private static Tenant firm(String settings) {
        Tenant tenant = new Tenant();
        tenant.setSettingsJson(settings);
        return tenant;
    }
}
