package com.legalsuite.importer;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.FinanceService;
import com.legalsuite.service.PracticeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * CSV import wizard: templates, validation preview (dry run), then commit. Every committed run is
 * audited. A run with any invalid row is refused as a whole so a half-imported book never happens.
 */
@Service
public class ImportService {
    public static final int MAX_ROWS = 5000;

    static final Map<String, List<String>> TEMPLATES = new LinkedHashMap<>();
    static final Map<String, String> EXAMPLES = new HashMap<>();
    static {
        TEMPLATES.put("clients", List.of("type", "first_name", "last_name", "company_name", "email", "phone", "address", "city", "province", "notes"));
        EXAMPLES.put("clients", "individual,Thandi,Mabaso,,thandi@example.co.za,0821234567,12 Long Street,Cape Town,Western Cape,Referred by bank");
        TEMPLATES.put("contacts", List.of("type", "first_name", "last_name", "company", "title", "email", "phone"));
        EXAMPLES.put("contacts", "advocate,Pieter,Botha,Group 621,Adv,pbotha@example.co.za,0115550000");
        TEMPLATES.put("matters", List.of("matter_number", "title", "client_email", "client_name", "practice_area", "case_type", "status", "court_name", "opposing_party", "billing_type", "billing_rate", "date_opened"));
        EXAMPLES.put("matters", "MAB001,Mabaso v Road Accident Fund,thandi@example.co.za,,Personal injury,raf,open,Gauteng Division Pretoria,Road Accident Fund,hourly,2200,2026-03-01");
        TEMPLATES.put("trust_opening", List.of("client_email", "client_name", "matter_number", "amount", "date", "reference"));
        EXAMPLES.put("trust_opening", "thandi@example.co.za,,MAB001,15000.00,2026-09-30,Opening balance from old system");
        TEMPLATES.put("time", List.of("date", "matter_number", "user_email", "minutes", "hourly_rate", "description", "activity_type", "billable"));
        EXAMPLES.put("time", "2026-10-01,MAB001,attorney@yourfirm.co.za,90,2200,Consultation with client,consultation,yes");
    }

    private final PracticeService practice;
    private final FinanceService finance;
    private final ClientRepository clients;
    private final LegalCaseRepository cases;
    private final AppUserRepository users;
    private final TimeEntryRepository times;
    private final TrustAccountRepository trusts;
    private final AuditService audit;

    public ImportService(PracticeService practice, FinanceService finance, ClientRepository clients, LegalCaseRepository cases,
                         AppUserRepository users, TimeEntryRepository times, TrustAccountRepository trusts, AuditService audit) {
        this.practice = practice;
        this.finance = finance;
        this.clients = clients;
        this.cases = cases;
        this.users = users;
        this.times = times;
        this.trusts = trusts;
        this.audit = audit;
    }

    public List<Map<String, Object>> kinds() {
        List<Map<String, Object>> out = new ArrayList<>();
        TEMPLATES.forEach((k, cols) -> out.add(Map.of("kind", k, "columns", cols)));
        return out;
    }

    public String template(String kind) {
        List<String> cols = cols(kind);
        return String.join(",", cols) + "\r\n" + EXAMPLES.get(kind) + "\r\n";
    }

    /** dryRun=true validates only. dryRun=false validates, then writes when every row is valid. */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> run(String kind, String csv, boolean dryRun, UUID trustAccountId) {
        cols(kind);
        List<Map<String, String>> rows = Csv.records(csv);
        if (rows.isEmpty()) throw ApiException.badRequest("The file has no data rows under the header.");
        if (rows.size() > MAX_ROWS) throw ApiException.badRequest("Split the file: at most " + MAX_ROWS + " rows per import.");
        UUID tid = TenantContext.requireTenant();
        TrustAccount acct = null;
        if ("trust_opening".equals(kind)) {
            if (trustAccountId == null) throw ApiException.badRequest("Choose the trust account the opening balances go into.");
            acct = trusts.findByIdAndTenantId(trustAccountId, tid).orElseThrow(() -> ApiException.notFound("Trust account not found"));
        }
        List<Map<String, Object>> preview = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int bad = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            List<String> errs = validate(kind, tid, r, seen);
            if (!errs.isEmpty()) bad++;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("line", i + 2);
            p.put("values", r);
            p.put("errors", errs);
            preview.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("rows", rows.size());
        out.put("valid", rows.size() - bad);
        out.put("invalid", bad);
        out.put("preview", preview.size() > 200 ? preview.subList(0, 200) : preview);
        out.put("dryRun", dryRun);
        if (dryRun || bad > 0) {
            out.put("imported", 0);
            if (!dryRun) out.put("message", "Nothing was imported. Fix the rows with errors and try again.");
            return out;
        }
        com.legalsuite.common.Roles.requireTenantAdmin("Only a firm owner, director or partner can import data.");
        int done = 0;
        for (Map<String, String> r : rows) {
            write(kind, tid, r, acct);
            done++;
        }
        out.put("imported", done);
        audit.record("import." + kind, "import", kind, done + " rows imported");
        return out;
    }

    private List<String> cols(String kind) {
        List<String> c = TEMPLATES.get(kind);
        if (c == null) throw ApiException.badRequest("Unknown import type. Use one of " + TEMPLATES.keySet() + ".");
        return c;
    }

    List<String> validate(String kind, UUID tid, Map<String, String> r, Set<String> seen) {
        List<String> e = new ArrayList<>();
        switch (kind) {
            case "clients" -> {
                if (blank(r, "first_name") && blank(r, "last_name") && blank(r, "company_name")) e.add("Give a name or a company name.");
                String email = v(r, "email");
                if (!email.isEmpty()) {
                    if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) e.add("Email address looks wrong.");
                    else if (!seen.add(email.toLowerCase(Locale.ROOT))) e.add("Same email twice in this file.");
                    else if (clients.findByTenantIdAndEmailIgnoreCase(tid, email).isPresent()) e.add("A client with this email already exists.");
                }
            }
            case "contacts" -> {
                if (blank(r, "first_name") && blank(r, "last_name") && blank(r, "company")) e.add("Give a name or a company.");
            }
            case "matters" -> {
                if (blank(r, "title")) e.add("Title is required.");
                String num = v(r, "matter_number");
                if (!num.isEmpty()) {
                    if (!seen.add(num.toLowerCase(Locale.ROOT))) e.add("Same matter number twice in this file.");
                    else if (cases.findByTenantIdAndCaseNumberIgnoreCase(tid, num).isPresent()) e.add("Matter number already in use.");
                }
                if (findClient(tid, r) == null) e.add("Client not found. Import clients first, or check client_email / client_name.");
                if (!blank(r, "billing_rate") && money(v(r, "billing_rate")) == null) e.add("Billing rate must be a number.");
                if (!blank(r, "date_opened") && date(v(r, "date_opened")) == null) e.add("Date must be YYYY-MM-DD or DD/MM/YYYY.");
            }
            case "trust_opening" -> {
                Client c = findClient(tid, r);
                if (c == null) e.add("Client not found.");
                BigDecimal amt = money(v(r, "amount"));
                if (amt == null || amt.signum() <= 0) e.add("Amount must be more than zero.");
                if (!blank(r, "matter_number")) {
                    LegalCase m = cases.findByTenantIdAndCaseNumberIgnoreCase(tid, v(r, "matter_number")).orElse(null);
                    if (m == null) e.add("Matter not found.");
                    else if (c != null && !c.getId().equals(m.getClientId())) e.add("That matter belongs to a different client.");
                }
                if (!blank(r, "date") && date(v(r, "date")) == null) e.add("Date must be YYYY-MM-DD or DD/MM/YYYY.");
            }
            case "time" -> {
                if (date(v(r, "date")) == null) e.add("Date must be YYYY-MM-DD or DD/MM/YYYY.");
                if (!blank(r, "matter_number") && cases.findByTenantIdAndCaseNumberIgnoreCase(tid, v(r, "matter_number")).isEmpty()) e.add("Matter not found.");
                if (!blank(r, "user_email") && users.findByTenantIdAndEmailIgnoreCase(tid, v(r, "user_email")).isEmpty()) e.add("No team member with that email.");
                int mins;
                try { mins = Integer.parseInt(v(r, "minutes")); } catch (NumberFormatException x) { mins = -1; }
                if (mins <= 0 || mins > 24 * 60) e.add("Minutes must be a whole number from 1 to 1440.");
                if (!blank(r, "hourly_rate") && money(v(r, "hourly_rate")) == null) e.add("Hourly rate must be a number.");
                if (blank(r, "description")) e.add("Description is required.");
            }
            default -> e.add("Unknown type");
        }
        return e;
    }

    private void write(String kind, UUID tid, Map<String, String> r, TrustAccount acct) {
        switch (kind) {
            case "clients" -> {
                Map<String, Object> b = new HashMap<>();
                b.put("type", blank(r, "type") ? (blank(r, "company_name") ? "individual" : "company") : v(r, "type"));
                b.put("firstName", v(r, "first_name"));
                b.put("lastName", v(r, "last_name"));
                b.put("companyName", v(r, "company_name"));
                b.put("email", v(r, "email"));
                b.put("phone", v(r, "phone"));
                b.put("addressLine1", v(r, "address"));
                b.put("city", v(r, "city"));
                b.put("state", v(r, "province"));
                b.put("notes", v(r, "notes"));
                b.put("source", "import");
                practice.saveClient(null, b);
            }
            case "contacts" -> {
                Map<String, Object> b = new HashMap<>();
                b.put("type", blank(r, "type") ? "other" : v(r, "type"));
                b.put("firstName", v(r, "first_name"));
                b.put("lastName", v(r, "last_name"));
                b.put("company", v(r, "company"));
                b.put("title", v(r, "title"));
                b.put("email", v(r, "email"));
                b.put("phone", v(r, "phone"));
                practice.saveContact(b);
            }
            case "matters" -> {
                Map<String, Object> b = new HashMap<>();
                b.put("clientId", findClient(tid, r).getId().toString());
                b.put("title", v(r, "title"));
                b.put("practiceArea", v(r, "practice_area"));
                b.put("caseType", v(r, "case_type"));
                b.put("status", blank(r, "status") ? "open" : v(r, "status"));
                b.put("courtName", v(r, "court_name"));
                b.put("opposingParty", v(r, "opposing_party"));
                b.put("billingType", blank(r, "billing_type") ? "hourly" : v(r, "billing_type"));
                if (!blank(r, "billing_rate")) b.put("billingRate", money(v(r, "billing_rate")).toPlainString());
                Map<String, Object> saved = practice.saveCase(null, b);
                LegalCase c = cases.findByIdAndTenantId(UUID.fromString(String.valueOf(saved.get("id"))), tid).orElseThrow();
                if (!blank(r, "matter_number")) c.setCaseNumber(v(r, "matter_number"));
                if (!blank(r, "date_opened")) c.setDateOpened(date(v(r, "date_opened")));
                cases.save(c);
            }
            case "trust_opening" -> {
                Map<String, Object> b = new HashMap<>();
                b.put("accountId", acct.getId().toString());
                b.put("type", "deposit");
                b.put("amount", money(v(r, "amount")).toPlainString());
                b.put("clientId", findClient(tid, r).getId().toString());
                if (!blank(r, "matter_number")) {
                    b.put("caseId", cases.findByTenantIdAndCaseNumberIgnoreCase(tid, v(r, "matter_number")).orElseThrow().getId().toString());
                }
                String d = blank(r, "date") ? "" : " as at " + date(v(r, "date"));
                b.put("description", "Opening balance (import)" + d + (blank(r, "reference") ? "" : ": " + v(r, "reference")));
                // Opening balances are money already held in trust under the old system, so the limited-file gate does not apply.
                finance.trustMove(b, true);
            }
            case "time" -> {
                TimeEntry t = new TimeEntry();
                t.setTenantId(tid);
                t.setDate(date(v(r, "date")));
                if (!blank(r, "matter_number")) t.setCaseId(cases.findByTenantIdAndCaseNumberIgnoreCase(tid, v(r, "matter_number")).orElseThrow().getId());
                AppUser u = blank(r, "user_email") ? null : users.findByTenantIdAndEmailIgnoreCase(tid, v(r, "user_email")).orElse(null);
                t.setUserId(u != null ? u.getId() : TenantContext.getUserId());
                int mins = Integer.parseInt(v(r, "minutes"));
                t.setDurationMinutes(mins);
                BigDecimal rate = blank(r, "hourly_rate") ? (u != null && u.getHourlyRate() != null ? u.getHourlyRate() : BigDecimal.ZERO) : money(v(r, "hourly_rate"));
                t.setHourlyRate(rate);
                t.setTotalAmount(rate.multiply(BigDecimal.valueOf(mins)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                t.setDescription(v(r, "description"));
                t.setActivityType(blank(r, "activity_type") ? "general" : v(r, "activity_type"));
                String bill = v(r, "billable").toLowerCase(Locale.ROOT);
                t.setBillable(!(bill.equals("no") || bill.equals("n") || bill.equals("false") || bill.equals("0")));
                t.setSource("import");
                times.save(t);
            }
            default -> throw ApiException.badRequest("Unknown type");
        }
    }

    private Client findClient(UUID tid, Map<String, String> r) {
        if (!blank(r, "client_email")) return clients.findByTenantIdAndEmailIgnoreCase(tid, v(r, "client_email")).orElse(null);
        String name = v(r, "client_name").toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return null;
        List<Client> hits = clients.findByTenantIdOrderByLastNameAsc(tid).stream()
                .filter(c -> c.displayName() != null && c.displayName().trim().toLowerCase(Locale.ROOT).equals(name)).toList();
        return hits.size() == 1 ? hits.get(0) : null;
    }

    static String v(Map<String, String> r, String k) { String s = r.get(k); return s == null ? "" : s.trim(); }
    static boolean blank(Map<String, String> r, String k) { return v(r, k).isEmpty(); }

    static BigDecimal money(String s) {
        try { return new BigDecimal(s.replace("R", "").replace(" ", "").replace(",", "")); } catch (Exception e) { return null; }
    }

    static LocalDate date(String s) {
        for (String f : List.of("yyyy-MM-dd", "dd/MM/yyyy", "yyyy/MM/dd")) {
            try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(f)); } catch (Exception ignored) { }
        }
        return null;
    }
}
