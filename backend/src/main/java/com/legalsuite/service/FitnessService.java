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
import org.springframework.stereotype.Service;

@Service
public class FitnessService {
    private final TenantRepository tenants;
    private final LegalCaseRepository cases;
    private final FinanceService finance;
    private final DashboardService dashboard;

    public FitnessService(
            TenantRepository tenants,
            LegalCaseRepository cases,
            FinanceService finance,
            DashboardService dashboard) {
        this.tenants = tenants;
        this.cases = cases;
        this.finance = finance;
        this.dashboard = dashboard;
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
        items.add(item("bank-feed", false, "you",
                "Live bank feed",
                "Import FNB / Standard Bank / ABSA CSV (or Plaid in the US) so the bank leg is not typed in."));
        items.add(item("ffc", false, "you",
                za ? "Fidelity Fund Certificate on file" : "Bar card / IOLTA enrollment",
                za
                        ? "LPA s 84: you may not hold trust money without a current FFC. Store the PDF on the firm record."
                        : "Confirm the firm IOLTA enrollment with the state bar."));
        items.add(item("popia", false, "you",
                za ? "POPIA operator + PAIA manual" : "Privacy policy / SOC2",
                za
                        ? "Appoint an information officer, keep a PAIA manual, and sign an operator agreement if a host processes client files."
                        : "Production needs a real privacy program, not a heuristic strip."));
        items.add(item("esign-cert", false, "you",
                "Certified electronic signature",
                "In-app sign is a hashed instrument for the demo. A world-class mandate uses ECT Act accredited signatures or DocuSign/Adobe with a certificate."));
        items.add(item("production-db", false, "you",
                "PostgreSQL, 2FA, TLS",
                "H2 create-drop is a demo. Inspectors and insurers will not accept an in-memory ledger."));
        items.add(item("caselines", false, "later",
                za ? "CaseLines / court e-filing" : "E-filing",
                "The docket can create the task. It cannot yet lodge the RAF 1 or upload a brief."));
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
                ? "Certify this month's three-way recon, then store the Fidelity Fund Certificate. That is what turns the demo into a practice."
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
