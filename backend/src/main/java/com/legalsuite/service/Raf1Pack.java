package com.legalsuite.service;

import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import java.time.LocalDate;
import java.util.List;

/** RAF Act s 24 lodging pack. The form the Fund expects, filled from the file. */
public final class Raf1Pack {
    private Raf1Pack() {}

    public static String render(Tenant firm, Client client, LegalCase c, TexasDocketRules.Result docket) {
        String claimant = client == null ? "Claimant" : client.displayName();
        String clocks = "";
        if (docket != null) {
            StringBuilder sb = new StringBuilder();
            for (TexasDocketRules.Clock clock : docket.clocks()) {
                sb.append("  - ").append(clock.title()).append(": ").append(clock.date())
                        .append(" (").append(clock.citation()).append(")\n");
            }
            clocks = sb.toString();
        }
        return """
                ROAD ACCIDENT FUND ACT 56 OF 1996, SECTION 24 CLAIM PACK
                (RAF 1 equivalent compiled from the matter. Lodge with the Fund; this is not e-filing to CaseLines.)

                Firm: %s
                FFC: %s (expires %s)
                Matter: %s  %s

                1. Claimant
                Name: %s
                Email: %s
                Phone: %s

                2. Accident
                Date of accident (cause of action): %s
                Description: %s
                Identified / unidentified: %s

                3. Defendant / Fund
                Opposing party: %s
                Court: %s

                4. Prescription (docket engine; confirm before lodging)
                %s
                5. Documents still required before the Fund will accept the claim
                - Completed RAF 1 form (this pack)
                - Medical reports (s 24)
                - Hospital records
                - SAPS accident report / officer's accident report
                - Identity document of the claimant
                - Proof of earnings if loss of income is claimed

                Compiled %s. Confirm every field against the client before you lodge.
                """.formatted(
                firm.getFirmName(),
                firm.getFfcNumber() == null ? "(no FFC on file)" : firm.getFfcNumber(),
                firm.getFfcExpiresOn() == null ? "not on file" : firm.getFfcExpiresOn().toString(),
                c.getCaseNumber(),
                c.getTitle(),
                claimant,
                client == null || client.getEmail() == null ? "" : client.getEmail(),
                client == null || client.getPhone() == null ? "" : client.getPhone(),
                c.getAccrualDate() == null ? "unknown" : c.getAccrualDate().toString(),
                c.getDescription() == null ? "" : c.getDescription(),
                c.isHitAndRun() ? "Hit-and-run / unidentified vehicle (s 23 two-year lodge)" : "Identified vehicle (s 23 three-year lodge)",
                c.getOpposingParty() == null ? "Road Accident Fund" : c.getOpposingParty(),
                c.getCourtName() == null ? "" : c.getCourtName(),
                clocks.isBlank() ? "  (no clocks stamped)\n" : clocks,
                LocalDate.now());
    }
}
