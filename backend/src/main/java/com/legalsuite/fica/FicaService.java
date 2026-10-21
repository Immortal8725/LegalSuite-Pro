package com.legalsuite.fica;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import com.legalsuite.common.Ids;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.domain.TaskItem;
import com.legalsuite.domain.Tenant;
import com.legalsuite.importer.Csv;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.TaskItemRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FicaService {
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private final FicaProfileRepository profiles;
    private final ClientRepository clients;
    private final DocumentFileRepository documents;
    private final TaskItemRepository tasks;
    private final TenantRepository tenants;
    private final AuditService audit;
    private final ObjectMapper json = new ObjectMapper();

    public FicaService(FicaProfileRepository profiles, ClientRepository clients, DocumentFileRepository documents,
                       TaskItemRepository tasks, TenantRepository tenants, AuditService audit) {
        this.profiles = profiles;
        this.clients = clients;
        this.documents = documents;
        this.tasks = tasks;
        this.tenants = tenants;
        this.audit = audit;
    }

    private static UUID tid() { return TenantContext.requireTenant(); }
    static LocalDate today() { return LocalDate.now(SAST); }

    public Map<String, Object> factors() {
        return Map.of("factors", FicaRules.FACTORS, "sanctionsSources", List.of(
                "UN Security Council Consolidated List: https://main.un.org/securitycouncil/en/content/un-sc-consolidated-list",
                "FIC targeted financial sanctions list: https://www.fic.gov.za/international-and-local-sanctions/",
                "Search news and public records for the client's name"));
    }

    public Map<String, Object> get(UUID clientId) {
        Client c = client(clientId);
        FicaProfile p = profiles.findByTenantIdAndClientId(tid(), clientId).orElse(null);
        return view(c, p);
    }

    @Transactional
    public Map<String, Object> save(UUID clientId, Map<String, Object> body) {
        Client c = client(clientId);
        FicaProfile p = profiles.findByTenantIdAndClientId(tid(), clientId).orElseGet(() -> {
            FicaProfile n = new FicaProfile();
            n.setTenantId(tid());
            n.setClientId(clientId);
            return n;
        });
        if (body.containsKey("idType")) p.setIdType(str(body.get("idType")));
        if (body.containsKey("idNumber")) {
            String id = str(body.get("idNumber"));
            if (id != null && "sa_id".equals(p.getIdType()) && !validSaId(id)) {
                throw ApiException.badRequest("That South African ID number fails the check digit. Check it against the ID document.");
            }
            p.setIdNumber(id);
        }
        if (body.containsKey("idExpiry")) p.setIdExpiry(date(body.get("idExpiry")));
        if (body.containsKey("poaDate")) p.setPoaDate(date(body.get("poaDate")));
        if (body.containsKey("idDocumentId")) p.setIdDocumentId(doc(body.get("idDocumentId"), clientId));
        if (body.containsKey("poaDocumentId")) p.setPoaDocumentId(doc(body.get("poaDocumentId"), clientId));
        if (body.containsKey("pepStatus")) {
            String pep = str(body.get("pepStatus"));
            if (pep != null && !List.of("yes", "no").contains(pep)) throw ApiException.badRequest("PEP status is yes or no.");
            p.setPepStatus(pep);
        }
        if (body.containsKey("sanctionsResult")) {
            String r = str(body.get("sanctionsResult"));
            if (r != null && !FicaRules.SANCTIONS_RESULTS.contains(r)) throw ApiException.badRequest("Sanctions result is clear, possible_match or match.");
            p.setSanctionsResult(r);
            p.setSanctionsCheckedAt(r == null ? null : Instant.now());
            p.setSanctionsSources(str(body.get("sanctionsSources")));
        }
        if (body.containsKey("sourceOfFunds")) p.setSourceOfFunds(str(body.get("sourceOfFunds")));
        if (body.containsKey("notes")) p.setNotes(str(body.get("notes")));
        if (body.containsKey("riskFactors")) {
            @SuppressWarnings("unchecked")
            List<String> f = ((List<Object>) body.get("riskFactors")).stream().map(String::valueOf)
                    .filter(FicaRules.FACTORS::containsKey).toList();
            try { p.setRiskFactorsJson(json.writeValueAsString(f)); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        int score = FicaRules.score(factorList(p));
        p.setRiskScore(score);
        String rating = FicaRules.rating(score, p.getPepStatus());
        if (body.get("riskRating") != null) {
            String override = String.valueOf(body.get("riskRating"));
            if (!List.of("low", "medium", "high").contains(override)) throw ApiException.badRequest("Risk is low, medium or high.");
            // A firm may rate higher than the score, never lower.
            if (rank(override) >= rank(rating)) rating = override;
        }
        p.setRiskRating(rating);
        recompute(p);
        p.setUpdatedAt(Instant.now());
        profiles.save(p);
        audit.record("fica.update", "client", clientId.toString(), "risk " + rating + ", status " + p.getStatus());
        return view(c, p);
    }

    @Transactional
    public Map<String, Object> verify(UUID clientId) {
        Client c = client(clientId);
        FicaProfile p = profiles.findByTenantIdAndClientId(tid(), clientId)
                .orElseThrow(() -> ApiException.badRequest("Capture the FICA details first."));
        if ("match".equals(p.getSanctionsResult())) {
            throw ApiException.badRequest("Sanctions match: do not act. Report to the FIC (s 28A) and get advice before going further.");
        }
        List<String> missing = FicaRules.missing(p, today());
        if (!missing.isEmpty()) throw ApiException.badRequest("Still missing: " + String.join(", ", missing) + ".");
        p.setVerifiedBy(TenantContext.requireUser());
        p.setVerifiedAt(Instant.now());
        p.setNextReviewDate(FicaRules.nextReview(p.getRiskRating(), today()));
        recompute(p);
        profiles.save(p);
        audit.record("fica.verify", "client", clientId.toString(), "verified, next review " + p.getNextReviewDate());
        return view(c, p);
    }

    void recompute(FicaProfile p) {
        LocalDate today = today();
        if ("match".equals(p.getSanctionsResult())) p.setStatus("blocked");
        else if (p.getVerifiedAt() == null) p.setStatus("incomplete");
        else if ((p.getNextReviewDate() != null && !p.getNextReviewDate().isAfter(today))
                || (p.getIdExpiry() != null && p.getIdExpiry().isBefore(today))) p.setStatus("review_due");
        else p.setStatus("verified");
    }

    public List<Map<String, Object>> report() {
        UUID tid = tid();
        Map<UUID, FicaProfile> byClient = new LinkedHashMap<>();
        for (FicaProfile p : profiles.findByTenantId(tid)) { recompute(p); byClient.put(p.getClientId(), p); }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Client c : clients.findByTenantIdOrderByLastNameAsc(tid)) out.add(view(c, byClient.get(c.getId())));
        return out;
    }

    public String reportCsv() {
        StringBuilder sb = new StringBuilder("client,status,risk,pep,sanctions,id_expiry,verified_at,next_review,missing\r\n");
        for (Map<String, Object> r : report()) {
            @SuppressWarnings("unchecked") List<String> missing = (List<String>) r.get("missing");
            sb.append(String.join(",", List.of(Csv.escape(r.get("clientName")), Csv.escape(r.get("status")), Csv.escape(r.get("riskRating")),
                    Csv.escape(r.get("pepStatus")), Csv.escape(r.get("sanctionsResult")), Csv.escape(r.get("idExpiry")),
                    Csv.escape(r.get("verifiedAt")), Csv.escape(r.get("nextReviewDate")), Csv.escape(String.join("; ", missing))))).append("\r\n");
        }
        audit.record("fica.report", "fica", "csv", "FICA register exported");
        return sb.toString();
    }

    /** Daily at 07:05 SAST: one task per client whose review is due within 30 days or whose ID has expired. */
    @Scheduled(cron = "0 5 7 * * *", zone = "Africa/Johannesburg")
    public void reminderJob() {
        for (Tenant t : tenants.findAll()) remind(t.getId());
    }

    @Transactional
    public int remind(UUID tenantId) {
        int made = 0;
        LocalDate today = today();
        for (FicaProfile p : profiles.findByTenantId(tenantId)) {
            LocalDate due = null;
            String why = null;
            if (p.getIdExpiry() != null && !p.getIdExpiry().isAfter(today.plusDays(30))) { due = p.getIdExpiry(); why = "ID document expires " + p.getIdExpiry(); }
            if (p.getNextReviewDate() != null && !p.getNextReviewDate().isAfter(today.plusDays(30))
                    && (due == null || p.getNextReviewDate().isBefore(due))) { due = p.getNextReviewDate(); why = "FICA review due " + p.getNextReviewDate(); }
            if (due == null) continue;
            String key = "fica:" + p.getClientId() + ":" + due;
            if (tasks.findByTenantIdAndSourceKey(tenantId, key).isPresent()) continue;
            Client c = clients.findByIdAndTenantId(p.getClientId(), tenantId).orElse(null);
            TaskItem task = new TaskItem();
            task.setTenantId(tenantId);
            task.setTitle("FICA: re-verify " + (c == null ? "client" : c.displayName()));
            task.setDescription(why + ". Update ID, proof of address, PEP and sanctions checks, then mark verified.");
            task.setPriority("high".equals(p.getRiskRating()) ? "high" : "medium");
            task.setDueDate(due.atStartOfDay(SAST).toInstant());
            task.setSourceKey(key);
            tasks.save(task);
            made++;
        }
        return made;
    }

    public byte[] rmcpDocx() {
        Tenant t = tenants.findById(tid()).orElseThrow();
        String firm = t.getFirmName();
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            h(d, firm + ": Risk Management and Compliance Programme (RMCP)", 16);
            p(d, "Prepared under section 42 of the Financial Intelligence Centre Act 38 of 2001 (FIC Act). Template generated by Owlaw on " + today()
                    + ". Review it, adapt it to your practice, and have the board or sole director approve it in writing. This template is not legal advice.");
            String[][] sections = {
                {"1. Scope and approval", "This RMCP applies to all partners, directors, attorneys, candidate attorneys and staff of " + firm + ". It is approved by the senior management of the firm and reviewed at least once a year. The compliance officer is: [name]."},
                {"2. Accountable institution", "The firm is an accountable institution under item 1 of Schedule 1 to the FIC Act and is registered with the FIC (goAML) under organisation ID [number]."},
                {"3. Risk-based approach", "Each client and each matter is rated low, medium or high risk before work starts. Risk factors used: politically exposed or prominent influential person; links to a high-risk country on the FATF lists; large or frequent cash; complex structures such as trusts or layered companies; clients never met face to face; instructions without a clear economic purpose; property and high value transfers; third-party funding. Owlaw scores these factors; the firm may rate a client higher than the score, never lower."},
                {"4. Customer due diligence (ss 21 to 21B)", "Before establishing a business relationship or concluding a single transaction we establish and verify the client's identity: natural persons by ID document or passport and proof of residential address; legal persons by registration documents, directors, and beneficial owners holding 5% or more or exercising control; trusts by the letters of authority, trustees, founders and beneficiaries. Copies are stored on the client's FICA record in Owlaw."},
                {"5. Enhanced due diligence", "For high-risk clients and PEPs or PIPs (ss 21F to 21H), senior management approves the relationship, we establish the source of funds and source of wealth, and review the relationship every 12 months."},
                {"6. Targeted financial sanctions (s 26B)", "Every new client and every person in a transaction is screened against the UN Security Council Consolidated List and the FIC targeted financial sanctions list. The result and the date are recorded. A match means we do not act, freeze any property and report to the FIC under section 28A without delay."},
                {"7. Ongoing due diligence", "Client information is kept up to date. Re-verification: high risk every 1 year, medium every 2 years, low every 3 years, and whenever an ID expires or the client's circumstances change. Owlaw creates a reminder task 30 days before each date."},
                {"8. Reporting", "Suspicious and unusual transactions (s 29) are reported on goAML within 15 business days. Cash transactions of R49,999.99 or more (s 28) are reported within 3 business days. Staff report internally to the compliance officer, who decides and records the outcome. No one may tip off the client."},
                {"9. Record keeping (ss 22 to 24)", "CDD records and transaction records are kept for at least 5 years after the relationship ends or the transaction is concluded, in Owlaw and the firm's document store."},
                {"10. Training", "All staff complete FIC Act training on joining and at least once a year. Training records are kept."},
                {"11. Trust account link", "No client money is received into trust until the client's FICA record is complete, except where the law allows completion afterwards and senior management has approved it in writing."},
            };
            for (String[] s : sections) { h(d, s[0], 12); p(d, s[1]); }
            p(d, "Approved by: ______________________   Date: ____________");
            d.write(out);
            audit.record("fica.rmcp", "tenant", t.getId().toString(), "RMCP template downloaded");
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void h(XWPFDocument d, String text, int size) {
        XWPFRun r = d.createParagraph().createRun();
        r.setBold(true); r.setFontSize(size); r.setText(text);
    }

    private static void p(XWPFDocument d, String text) {
        XWPFParagraph para = d.createParagraph();
        XWPFRun r = para.createRun();
        r.setFontSize(11); r.setText(text);
    }

    private Map<String, Object> view(Client c, FicaProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientId", c.getId());
        m.put("clientName", c.displayName());
        m.put("clientType", c.getType());
        if (p == null) {
            m.put("status", "not_started");
            m.put("missing", List.of("FICA record not started"));
            m.put("riskFactors", List.of());
            return m;
        }
        m.put("status", p.getStatus());
        m.put("riskRating", p.getRiskRating());
        m.put("riskScore", p.getRiskScore());
        m.put("riskFactors", factorList(p));
        m.put("idType", p.getIdType());
        m.put("idNumberMasked", mask(p.getIdNumber()));
        m.put("idExpiry", p.getIdExpiry());
        m.put("idDocumentId", p.getIdDocumentId());
        m.put("poaDate", p.getPoaDate());
        m.put("poaDocumentId", p.getPoaDocumentId());
        m.put("pepStatus", p.getPepStatus());
        m.put("sanctionsResult", p.getSanctionsResult());
        m.put("sanctionsCheckedAt", p.getSanctionsCheckedAt());
        m.put("sanctionsSources", p.getSanctionsSources());
        m.put("sourceOfFunds", p.getSourceOfFunds());
        m.put("verifiedAt", p.getVerifiedAt());
        m.put("nextReviewDate", p.getNextReviewDate());
        m.put("notes", p.getNotes());
        m.put("missing", FicaRules.missing(p, today()));
        return m;
    }

    private List<String> factorList(FicaProfile p) {
        if (p.getRiskFactorsJson() == null) return List.of();
        try { return json.readValue(p.getRiskFactorsJson(), new TypeReference<List<String>>() {}); } catch (Exception e) { return List.of(); }
    }

    static String mask(String id) {
        if (id == null || id.length() < 5) return id;
        return "*".repeat(id.length() - 4) + id.substring(id.length() - 4);
    }

    /** Luhn check used by South African ID numbers (13 digits). */
    static boolean validSaId(String id) {
        if (!id.matches("\\d{13}")) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int d = id.charAt(12 - i) - '0';
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        return sum % 10 == 0;
    }

    private static int rank(String r) { return switch (r) { case "high" -> 3; case "medium" -> 2; default -> 1; }; }

    private Client client(UUID id) {
        return clients.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
    }

    private UUID doc(Object v, UUID clientId) {
        UUID id = Ids.optUuid(v, "document");
        if (id == null) return null;
        DocumentFile d = documents.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Document not found"));
        if (d.getClientId() != null && !d.getClientId().equals(clientId)) throw ApiException.badRequest("That document belongs to another client.");
        return id;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate date(Object v) {
        String s = str(v);
        return s == null ? null : LocalDate.parse(s);
    }
}
