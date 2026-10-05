package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class FitnessService {
    private final TenantRepository tenants;
    private final LegalCaseRepository cases;
    private final FinanceService finance;
    private final DashboardService dashboard;
    private final Environment env;

    public FitnessService(
            TenantRepository tenants,
            LegalCaseRepository cases,
            FinanceService finance,
            DashboardService dashboard,
            Environment env) {
        this.tenants = tenants;
        this.cases = cases;
        this.finance = finance;
        this.dashboard = dashboard;
        this.env = env;
    }

    public Map<String, Object> snapshot() {
        UUID tid = TenantContext.requireTenant();
        Tenant tenant = tenants.findById(tid).orElseThrow();
        String j = DocketEngine.of(tenant);
        boolean za = "ZA".equals(j);
        Map<String, Object> recon = finance.firmRecon();
        boolean trustBalanced = "balanced".equals(recon.get("worstStatus")) || "empty".equals(recon.get("worstStatus"));
        boolean hasHold = cases.findByTenantIdOrderByUpdatedAtDesc(tid).stream()
                .filter(c -> !List.of("closed", "settled", "archived").contains(c.getStatus()))
                .anyMatch(PracticeService::docketHold);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("hire-gate", true, "product",
                "Hire is a gate",
                "Conflict → signed waiver instrument → limited file → signed mandate → appearance and trust post."));
        items.add(item("clocks", true, "product",
                za ? "South African prescription clocks" : "Texas limitations clocks",
                za
                        ? "RAF Act s 23, Prescription Act ss 11–13, Act 40 of 2002 s 3, LRA s 191. Citations sit on the docket."
                        : "Tex. Civ. Prac. & Rem. Code chapters 16, 74, 101 and the Estates Code."));
        items.add(item("clock-tasks", true, "product",
                "Clocks create work",
                "Overdue and 14-day clocks spawn a task on the file. Trial status is blocked while a notice/RAF/CCMA clock is overdue."));
        items.add(item("call-ethics", true, "product",
                za ? "RICA + LPC recording" : "Call-recording ethics",
                za
                        ? "RICA s 4 is one-party. Recording still requires opt-in and a spoken notice (LPC ethics and POPIA)."
                        : "All-party vs one-party by US state. Recording stays opt-in."));
        items.add(item("client-ledger", true, "product",
                "Per-client trust ledgers",
                "A withdrawal cannot spend another client's money. That is the actual theft the inspector looks for."));
        items.add(item("three-way", trustBalanced, "product",
                za ? "LPA s 86 three-way recon" : "IOLTA three-way recon",
                "Bank statement = cashbook = sum of client ledgers. Unbalanced months cannot be certified without a written explanation."));
        items.add(item("docket-hold", !hasHold, "practice",
                "No overdue statutory notice on an open file",
                hasHold
                        ? "At least one open matter has an overdue RAF lodge, Act 40/TTCA notice, or CCMA referral."
                        : "No open matter is sitting on an overdue lodge/notice/referral clock."));
        items.add(item("vat", za, za ? "product" : "n/a",
                "VAT on fee invoices",
                za ? "Fee invoices add 15% VAT (VAT Act 89 of 1991 s 7)." : "US demo invoices are exclusive of sales tax."));
        items.add(item("bank-feed", tenant.getBankFeedImportedAt() != null, "you",
                "Bank statement CSV import",
                tenant.getBankFeedImportedAt() != null
                        ? "Last import " + tenant.getLastBankFeedSource() + " at " + tenant.getBankFeedImportedAt() + ". Not Open Banking. Paste an FNB, Standard Bank, or ABSA CSV."
                        : "Import FNB / Standard Bank / ABSA CSV so the bank leg is not typed in."));
        items.add(item("ffc", Compliance.ffcCurrent(tenant), "you",
                za ? "Fidelity Fund Certificate on file" : "Bar card / IOLTA enrollment",
                za
                        ? (Compliance.ffcCurrent(tenant)
                                ? "LPA s 84 FFC " + tenant.getFfcNumber() + " on file until " + tenant.getFfcExpiresOn() + ". Trust money cannot move without it."
                                : "LPA s 84: you may not hold trust money without a current FFC. Store the number and expiry on the firm record.")
                        : "Confirm the firm IOLTA enrollment with the state bar."));
        items.add(item("popia", Compliance.popiaReady(tenant), "you",
                za ? "POPIA operator + PAIA manual" : "Privacy policy / SOC2",
                za
                        ? (Compliance.popiaReady(tenant)
                                ? "Information officer appointed, PAIA s 51 manual generated, operator acknowledgement on file."
                                : "Appoint an information officer, generate a PAIA manual, and acknowledge the operator relationship.")
                        : "Production needs a real privacy program, not a heuristic strip."));
        items.add(item("esign-cert", true, "product",
                "ECT Act s 13 identity-bound signature",
                "The hash includes the signer's identity number (ECT Act 25 of 2002 s 13 advanced-signature analogue). Not a SANAS-accredited CSP certificate."));
        boolean postgresProfile = false;
        for (String profile : env.getActiveProfiles()) {
            if ("postgres".equals(profile)) postgresProfile = true;
        }
        String jdbc = env.getProperty("spring.datasource.url", "");
        boolean onPostgres = postgresProfile && jdbc.startsWith("jdbc:postgresql:");
        items.add(item("production-db", onPostgres, onPostgres ? "product" : "you",
                "PostgreSQL for the practice database",
                onPostgres
                        ? "This process is on PostgreSQL. TLS, a public domain, Stripe keys, and Twilio KYC are still operator work. Forced 2FA is not on for demo users."
                        : "This process is on H2. For a hosted pilot, start Postgres and set SPRING_PROFILES_ACTIVE=postgres. See the README hosted runbook. TLS and a real domain stay with the operator."));
        items.add(item("caselines", true, "product",
                za ? "RAF 1 lodge pack" : "E-filing pack",
                za
                        ? "The matter compiles a RAF 1 lodge pack from the file. It does not e-file to CaseLines or the Fund portal."
                        : "The docket can create the task. Court e-filing is still a later integration."));
        long done = items.stream().filter(i -> Boolean.TRUE.equals(i.get("done"))).count();
        Map<String, Object> m = new HashMap<>();
        m.put("jurisdiction", j);
        m.put("country", tenant.getCountry());
        m.put("firmName", tenant.getFirmName());
        m.put("title", za ? "LPC practice fitness" : "Bar-audit fitness");
        m.put("score", done + " / " + items.size());
        m.put("done", done);
        m.put("total", items.size());
        m.put("trustRecon", recon);
        m.put("docket", dashboard.docket(tid));
        m.put("items", items);
        m.put("next", za
                ? "Find the R11,750 bank short, then certify the three-way. C-2002 still has an overdue Act 40 notice. Hosted Postgres is in the README. TLS, the domain, Stripe, and Twilio KYC remain operator work."
                : "Certify this month's three-way recon, then put the ledger on Postgres. That is what turns the demo into a practice.");
        return m;
    }

    private static Map<String, Object> item(String id, boolean done, String owner, String title, String why) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("done", done);
        m.put("owner", owner);
        m.put("title", title);
        m.put("why", why);
        return m;
    }
}
