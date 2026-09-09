package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.domain.Tenant;
import java.time.LocalDate;

public final class Compliance {
    private Compliance() {}

    public static boolean ffcCurrent(Tenant t) {
        if (t == null) return false;
        if (t.getFfcNumber() == null || t.getFfcNumber().isBlank()) return false;
        if (t.getFfcExpiresOn() == null) return false;
        return !t.getFfcExpiresOn().isBefore(LocalDate.now());
    }

    public static boolean popiaReady(Tenant t) {
        if (t == null) return false;
        boolean io = t.getInformationOfficerName() != null && !t.getInformationOfficerName().isBlank()
                && t.getInformationOfficerEmail() != null && t.getInformationOfficerEmail().contains("@");
        boolean paia = t.getPaiaManualBody() != null && t.getPaiaManualBody().length() > 80;
        return io && paia && t.isPopiaOperatorAcknowledged();
    }

    public static void requireFfcForTrust(Tenant t) {
        if (t == null || !"ZA".equals(DocketEngine.of(t))) return;
        if (!ffcCurrent(t)) {
            throw ApiException.badRequest(
                    "Legal Practice Act s 84: no current Fidelity Fund Certificate on the firm record. Trust money cannot move.");
        }
    }
}
