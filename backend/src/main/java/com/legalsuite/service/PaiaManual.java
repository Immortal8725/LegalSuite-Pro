package com.legalsuite.service;

import com.legalsuite.domain.Tenant;
import java.time.LocalDate;

public final class PaiaManual {
    private PaiaManual() {}

    public static String generate(Tenant t) {
        String firm = t.getFirmName() == null ? "the firm" : t.getFirmName();
        String io = t.getInformationOfficerName() == null || t.getInformationOfficerName().isBlank()
                ? "the information officer"
                : t.getInformationOfficerName();
        String email = t.getInformationOfficerEmail() == null ? (t.getEmail() == null ? "" : t.getEmail())
                : t.getInformationOfficerEmail();
        String addr = String.join(", ",
                java.util.stream.Stream.of(t.getAddressLine1(), t.getCity(), t.getState(), t.getZip())
                        .filter(s -> s != null && !s.isBlank())
                        .toList());
        return """
                MANUAL IN TERMS OF SECTION 51 OF THE PROMOTION OF ACCESS TO INFORMATION ACT 2 OF 2000
                (read with the Protection of Personal Information Act 4 of 2013)

                1. Body
                %s, a trust-account practice under the Legal Practice Act 28 of 2014.

                2. Information officer
                %s
                %s
                %s

                3. Description of records
                Client mandates, matter files, trust ledgers, time records, invoices, and correspondence held for the purpose of providing legal services.

                4. Grounds for refusal
                Attorney-client privilege, litigation privilege, and POPIA sections 11–12 and 18. Privileged client text does not leave this tenant.

                5. Request procedure
                Address a PAIA Form 2 to the information officer. The firm will respond within the statutory period unless a longer period is permitted.

                6. Operator
                Hosting of this practice system is an operator relationship under POPIA s 20–22. Client files stay on the tenant row.

                Generated %s. This is a starting manual, not a substitute for counsel on PAIA/POPIA.
                """.formatted(firm, io, email, addr, LocalDate.now());
    }
}
