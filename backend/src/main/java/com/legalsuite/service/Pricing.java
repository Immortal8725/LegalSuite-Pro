package com.legalsuite.service;

import java.math.BigDecimal;

/**
 * Solo South African launch prices. The seat is a subscription. The phone is usage.
 * There is no included minute bundle and no unlimited voice tier.
 */
public final class Pricing {
    public static final String LIGHT_SLUG = "light";
    public static final BigDecimal LIGHT_MONTHLY_ZAR = new BigDecimal("1199");
    public static final int LIGHT_ATTORNEYS = 1;
    /** Public-network minutes included with the seat. Zero means pay-what-you-use. */
    public static final int INCLUDED_PSTN_MINUTES = 0;
    /** Optional local number. About R79 per month, or bundled when the operator includes it. */
    public static final BigDecimal DID_MONTHLY_ZAR = new BigDecimal("79");

    private Pricing() {}

    /** Negative values used to mean unlimited. The pilot does not sell unlimited voice. */
    public static int capIncludedMinutes(Integer raw) {
        if (raw == null || raw < 0) return INCLUDED_PSTN_MINUTES;
        return raw;
    }
}
