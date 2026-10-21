package com.legalsuite.reports;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Ids;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TrustAccount;
import com.legalsuite.domain.TrustReconciliation;
import com.legalsuite.domain.TrustTransaction;
import com.legalsuite.importer.Csv;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TrustAccountRepository;
import com.legalsuite.repo.TrustReconciliationRepository;
import com.legalsuite.repo.TrustTransactionRepository;
import com.legalsuite.service.AuditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Section 86 audit pack for the firm's auditor: trust cashbook, client ledger balances, monthly
 * reconciliations, three-way check at period end, s 86(3) and 86(4) investments with interest, and
 * an exceptions list. Exports JSON, CSV and PDF.
 */
@Service
public class TrustAuditService {
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private final TrustAccountRepository accounts;
    private final TrustTransactionRepository txs;
    private final TrustReconciliationRepository recons;
    private final TrustInvestmentRepository investments;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final AuditService audit;

    public TrustAuditService(TrustAccountRepository accounts, TrustTransactionRepository txs, TrustReconciliationRepository recons,
                             TrustInvestmentRepository investments, ClientRepository clients, TenantRepository tenants, AuditService audit) {
        this.accounts = accounts;
        this.txs = txs;
        this.recons = recons;
        this.investments = investments;
        this.clients = clients;
        this.tenants = tenants;
        this.audit = audit;
    }

    private static UUID tid() { return TenantContext.requireTenant(); }

    // ---- Investments ----------------------------------------------------------------------------

    public List<Map<String, Object>> listInvestments() {
        Map<UUID, String> names = clientNames();
        return investments.findByTenantIdOrderByOpenedOnDesc(tid()).stream().map(i -> invView(i, names)).toList();
    }

    @Transactional
    public Map<String, Object> saveInvestment(UUID id, Map<String, Object> b) {
        TrustInvestment i = id == null ? new TrustInvestment()
                : investments.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Investment not found"));
        i.setTenantId(tid());
        String section = String.valueOf(b.getOrDefault("section", i.getSection() == null ? "86(4)" : i.getSection()));
        if (!List.of("86(3)", "86(4)").contains(section)) throw ApiException.badRequest("Section is 86(3) or 86(4).");
        i.setSection(section);
        if (b.containsKey("trustAccountId")) i.setTrustAccountId(Ids.optUuid(b.get("trustAccountId"), "trust account"));
        if (b.containsKey("clientId")) {
            UUID c = Ids.optUuid(b.get("clientId"), "client");
            if (c != null) clients.findByIdAndTenantId(c, tid()).orElseThrow(() -> ApiException.notFound("Client not found"));
            i.setClientId(c);
        }
        if ("86(4)".equals(section) && i.getClientId() == null) throw ApiException.badRequest("A section 86(4) investment is made for one client. Choose the client.");
        if (b.containsKey("institution")) i.setInstitution(s(b.get("institution")));
        if (b.containsKey("accountNumber")) i.setAccountNumber(s(b.get("accountNumber")));
        if (b.containsKey("principal")) i.setPrincipal(money(b.get("principal")));
        if (b.containsKey("interestEarned")) i.setInterestEarned(money(b.get("interestEarned")));
        if (b.containsKey("interestPaidOut")) i.setInterestPaidOut(money(b.get("interestPaidOut")));
        if (b.containsKey("openedOn")) i.setOpenedOn(LocalDate.parse(String.valueOf(b.get("openedOn"))));
        if (b.containsKey("closedOn")) i.setClosedOn(s(b.get("closedOn")) == null ? null : LocalDate.parse(String.valueOf(b.get("closedOn"))));
        if (b.containsKey("notes")) i.setNotes(s(b.get("notes")));
        if (i.getOpenedOn() == null) i.setOpenedOn(LocalDate.now(SAST));
        if (i.getPrincipal().signum() <= 0) throw ApiException.badRequest("Principal must be more than zero.");
        investments.save(i);
        audit.record(id == null ? "trust.investment.create" : "trust.investment.update", "trust_investment", i.getId().toString(), section + " " + i.getPrincipal());
        return invView(i, clientNames());
    }

    // ---- Pack -----------------------------------------------------------------------------------

    public Map<String, Object> pack(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw ApiException.badRequest("Choose a period: from date on or before to date.");
        UUID tid = tid();
        Tenant firm = tenants.findById(tid).orElseThrow();
        Map<UUID, String> names = clientNames();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("firm", firm.getFirmName());
        out.put("ffcNumber", firm.getFfcNumber());
        out.put("ffcExpiresOn", firm.getFfcExpiresOn());
        out.put("from", from);
        out.put("to", to);
        out.put("generatedAt", java.time.ZonedDateTime.now(SAST).toString());
        List<Map<String, Object>> exceptions = new ArrayList<>();
        if (firm.getFfcExpiresOn() == null || firm.getFfcExpiresOn().isBefore(to)) {
            exceptions.add(ex("high", "Fidelity Fund Certificate", "No current FFC for the whole period (s 84). Expiry: " + firm.getFfcExpiresOn()));
        }
        List<Map<String, Object>> accts = new ArrayList<>();
        for (TrustAccount a : accounts.findByTenantId(tid)) {
            List<TrustTransaction> all = new ArrayList<>(txs.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(tid, a.getId()));
            all.sort(Comparator.comparing(TrustTransaction::getCreatedAt));
            BigDecimal opening = BigDecimal.ZERO;
            BigDecimal receipts = BigDecimal.ZERO;
            BigDecimal payments = BigDecimal.ZERO;
            Map<UUID, BigDecimal> ledgerAtEnd = new HashMap<>();
            Map<UUID, LocalDate> lastMove = new HashMap<>();
            BigDecimal unallocated = BigDecimal.ZERO;
            List<Map<String, Object>> cashbook = new ArrayList<>();
            BigDecimal running = BigDecimal.ZERO;
            for (TrustTransaction t : all) {
                LocalDate d = t.getCreatedAt().atZone(SAST).toLocalDate();
                if (d.isAfter(to)) break;
                BigDecimal signed = out(t.getType()) ? t.getAmount().negate() : t.getAmount();
                running = running.add(signed);
                if (t.getClientId() != null) {
                    ledgerAtEnd.merge(t.getClientId(), signed, BigDecimal::add);
                    lastMove.put(t.getClientId(), d);
                } else unallocated = unallocated.add(signed);
                if (d.isBefore(from)) { opening = running; continue; }
                if (signed.signum() >= 0) receipts = receipts.add(signed); else payments = payments.add(signed.negate());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", d);
                row.put("type", t.getType());
                row.put("client", t.getClientId() == null ? "UNALLOCATED" : names.getOrDefault(t.getClientId(), t.getClientId().toString()));
                row.put("description", t.getDescription());
                row.put("reference", t.getReferenceNumber());
                row.put("debit", signed.signum() < 0 ? signed.negate() : null);
                row.put("credit", signed.signum() >= 0 ? signed : null);
                row.put("balance", running);
                cashbook.add(row);
                if (out(t.getType()) && (t.getDescription() == null || t.getDescription().isBlank() || t.getDescription().equals(t.getType()))) {
                    exceptions.add(ex("medium", "Payment without narration", d + " " + t.getAmount() + " from " + a.getAccountName()));
                }
            }
            BigDecimal cashbookEnd = running;
            BigDecimal ledgerTotal = ledgerAtEnd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Map<String, Object>> ledgers = new ArrayList<>();
            ledgerAtEnd.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(k -> names.getOrDefault(k, "")))).forEach(e -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("client", names.getOrDefault(e.getKey(), e.getKey().toString()));
                r.put("balance", e.getValue());
                r.put("lastMovement", lastMove.get(e.getKey()));
                ledgers.add(r);
                if (e.getValue().signum() < 0) exceptions.add(ex("high", "Debit client balance", r.get("client") + ": " + e.getValue() + " (trust money of other clients used, LPC rule 54.14)"));
                if (e.getValue().signum() > 0 && lastMove.get(e.getKey()) != null && lastMove.get(e.getKey()).isBefore(to.minusMonths(12))) {
                    exceptions.add(ex("low", "Dormant balance", r.get("client") + ": " + e.getValue() + " with no movement for 12 months"));
                }
            });
            if (unallocated.signum() != 0) exceptions.add(ex("high", "Unallocated trust money", a.getAccountName() + ": " + unallocated + " not on any client ledger"));
            // Reconciliations in period, and months without a certified one.
            List<TrustReconciliation> rs = recons.findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(tid, a.getId()).stream()
                    .filter(r -> r.getPeriodEnd() != null && !r.getPeriodEnd().isBefore(from) && !r.getPeriodEnd().isAfter(to))
                    .sorted(Comparator.comparing(TrustReconciliation::getPeriodEnd)).toList();
            List<Map<String, Object>> reconRows = new ArrayList<>();
            for (TrustReconciliation r : rs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("periodEnd", r.getPeriodEnd());
                m.put("bank", r.getBankBalance());
                m.put("cashbook", r.getBookBalance());
                m.put("clientLedgers", r.getClientLedgerTotal());
                m.put("difference", r.getDifference());
                m.put("status", r.getStatus());
                m.put("certified", r.isCertified());
                reconRows.add(m);
                if (!"balanced".equals(r.getStatus())) exceptions.add(ex("high", "Unbalanced reconciliation", a.getAccountName() + " " + r.getPeriodEnd() + " difference " + r.getDifference()));
            }
            for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                YearMonth month = m;
                if (month.atEndOfMonth().isAfter(LocalDate.now(SAST))) break;
                boolean done = rs.stream().anyMatch(r -> YearMonth.from(r.getPeriodEnd()).equals(month) && r.isCertified());
                if (!done) exceptions.add(ex("high", "No certified monthly reconciliation", a.getAccountName() + " " + month));
            }
            BigDecimal bank = rs.isEmpty() ? a.getBankBalance() : rs.get(rs.size() - 1).getBankBalance();
            Map<String, Object> three = new LinkedHashMap<>();
            three.put("bank", bank);
            three.put("cashbook", cashbookEnd);
            three.put("clientLedgers", ledgerTotal.add(unallocated));
            boolean balanced = bank != null && bank.compareTo(cashbookEnd) == 0 && cashbookEnd.compareTo(ledgerTotal.add(unallocated)) == 0;
            three.put("balanced", balanced);
            if (!balanced) exceptions.add(ex("high", "Three-way reconciliation does not balance at period end",
                    a.getAccountName() + ": bank " + bank + ", cashbook " + cashbookEnd + ", client ledgers " + ledgerTotal.add(unallocated)));
            Map<String, Object> acc = new LinkedHashMap<>();
            acc.put("account", a.getAccountName());
            acc.put("bank", a.getBankName());
            acc.put("opening", opening);
            acc.put("receipts", receipts);
            acc.put("payments", payments);
            acc.put("closing", cashbookEnd);
            acc.put("cashbook", cashbook);
            acc.put("clientLedgers", ledgers);
            acc.put("reconciliations", reconRows);
            acc.put("threeWay", three);
            accts.add(acc);
        }
        out.put("accounts", accts);
        List<Map<String, Object>> inv = new ArrayList<>();
        BigDecimal int3 = BigDecimal.ZERO, int4 = BigDecimal.ZERO;
        for (TrustInvestment i : investments.findByTenantIdOrderByOpenedOnDesc(tid)) {
            if (i.getOpenedOn().isAfter(to) || (i.getClosedOn() != null && i.getClosedOn().isBefore(from))) continue;
            inv.add(invView(i, names));
            if ("86(3)".equals(i.getSection())) int3 = int3.add(i.getInterestEarned()); else int4 = int4.add(i.getInterestEarned());
            if ("86(3)".equals(i.getSection()) && i.getInterestEarned().compareTo(i.getInterestPaidOut()) > 0 && i.getClosedOn() != null) {
                exceptions.add(ex("medium", "s 86(3) interest not paid to the Fund", (i.getInstitution() == null ? "" : i.getInstitution()) + " " + i.getInterestEarned().subtract(i.getInterestPaidOut())));
            }
        }
        out.put("investments", inv);
        out.put("interest86_3", int3);
        out.put("interest86_4", int4);
        if (accts.isEmpty()) exceptions.add(ex("medium", "No trust account", "The firm has no trust account set up in Owlaw."));
        out.put("exceptions", exceptions);
        audit.record("trust.audit_pack", "trust", from + ".." + to, exceptions.size() + " exceptions");
        return out;
    }

    @SuppressWarnings("unchecked")
    public String csv(LocalDate from, LocalDate to) {
        Map<String, Object> p = pack(from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("Section 86 trust audit pack,").append(Csv.escape(p.get("firm"))).append(",").append(from).append(" to ").append(to).append("\r\n\r\n");
        for (Map<String, Object> a : (List<Map<String, Object>>) p.get("accounts")) {
            sb.append("TRUST CASHBOOK,").append(Csv.escape(a.get("account"))).append("\r\n");
            sb.append("date,type,client,description,reference,debit,credit,balance\r\n");
            sb.append(",,,Opening balance,,,,").append(a.get("opening")).append("\r\n");
            for (Map<String, Object> r : (List<Map<String, Object>>) a.get("cashbook")) {
                sb.append(String.join(",", List.of(c(r.get("date")), c(r.get("type")), c(r.get("client")), c(r.get("description")),
                        c(r.get("reference")), c(r.get("debit")), c(r.get("credit")), c(r.get("balance"))))).append("\r\n");
            }
            sb.append("\r\nCLIENT LEDGER BALANCES AT ").append(to).append("\r\nclient,balance,last_movement\r\n");
            for (Map<String, Object> r : (List<Map<String, Object>>) a.get("clientLedgers")) {
                sb.append(c(r.get("client"))).append(",").append(c(r.get("balance"))).append(",").append(c(r.get("lastMovement"))).append("\r\n");
            }
            sb.append("\r\nRECONCILIATIONS\r\nperiod_end,bank,cashbook,client_ledgers,difference,status,certified\r\n");
            for (Map<String, Object> r : (List<Map<String, Object>>) a.get("reconciliations")) {
                sb.append(String.join(",", List.of(c(r.get("periodEnd")), c(r.get("bank")), c(r.get("cashbook")), c(r.get("clientLedgers")),
                        c(r.get("difference")), c(r.get("status")), c(r.get("certified"))))).append("\r\n");
            }
            Map<String, Object> t = (Map<String, Object>) a.get("threeWay");
            sb.append("\r\nTHREE-WAY AT ").append(to).append("\r\nbank,cashbook,client_ledgers,balanced\r\n")
                    .append(c(t.get("bank"))).append(",").append(c(t.get("cashbook"))).append(",").append(c(t.get("clientLedgers"))).append(",").append(c(t.get("balanced"))).append("\r\n\r\n");
        }
        sb.append("INVESTMENTS\r\nsection,client,institution,account,principal,interest_earned,interest_paid_out,opened,closed\r\n");
        for (Map<String, Object> i : (List<Map<String, Object>>) p.get("investments")) {
            sb.append(String.join(",", List.of(c(i.get("section")), c(i.get("client")), c(i.get("institution")), c(i.get("accountNumber")),
                    c(i.get("principal")), c(i.get("interestEarned")), c(i.get("interestPaidOut")), c(i.get("openedOn")), c(i.get("closedOn"))))).append("\r\n");
        }
        sb.append("\r\nEXCEPTIONS\r\nseverity,check,detail\r\n");
        for (Map<String, Object> e : (List<Map<String, Object>>) p.get("exceptions")) {
            sb.append(c(e.get("severity"))).append(",").append(c(e.get("check"))).append(",").append(c(e.get("detail"))).append("\r\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public byte[] pdf(LocalDate from, LocalDate to) {
        Map<String, Object> p = pack(from, to);
        try (SimplePdf pdf = new SimplePdf(p.get("firm") + " | Section 86 trust audit pack " + from + " to " + to + " | Generated by Owlaw " + p.get("generatedAt").toString().substring(0, 16))) {
            pdf.title("Section 86 trust audit pack");
            pdf.para(p.get("firm") + ". Period " + from + " to " + to + ". Fidelity Fund Certificate " + (p.get("ffcNumber") == null ? "not recorded" : p.get("ffcNumber") + ", expires " + p.get("ffcExpiresOn")) + ".");
            pdf.para("Prepared for the firm's auditor under section 86 of the Legal Practice Act 28 of 2014 and the LPC Rules. Figures come from the Owlaw trust records. The auditor should agree the bank figures to the bank statements.");
            List<Map<String, Object>> ex = (List<Map<String, Object>>) p.get("exceptions");
            pdf.heading("Exceptions (" + ex.size() + ")");
            if (ex.isEmpty()) pdf.para("No exceptions found.");
            else pdf.table(List.of("Severity", "Check", "Detail"), ex.stream().map(e -> List.of(c(e.get("severity")), c(e.get("check")), c(e.get("detail")))).toList(),
                    new float[] {0.1f, 0.3f, 0.6f}, null);
            boolean[] money3 = {false, true, true, true, true};
            for (Map<String, Object> a : (List<Map<String, Object>>) p.get("accounts")) {
                pdf.heading("Trust account: " + a.get("account") + (a.get("bank") == null ? "" : " (" + a.get("bank") + ")"));
                Map<String, Object> t = (Map<String, Object>) a.get("threeWay");
                pdf.table(List.of("Summary", "Opening", "Receipts", "Payments", "Closing"),
                        List.of(List.of("Cashbook", r(a.get("opening")), r(a.get("receipts")), r(a.get("payments")), r(a.get("closing")))),
                        new float[] {0.32f, 0.17f, 0.17f, 0.17f, 0.17f}, money3);
                pdf.table(List.of("Three-way at " + to, "Bank", "Cashbook", "Client ledgers", "Result"),
                        List.of(List.of("", r(t.get("bank")), r(t.get("cashbook")), r(t.get("clientLedgers")), Boolean.TRUE.equals(t.get("balanced")) ? "Balanced" : "NOT BALANCED")),
                        new float[] {0.32f, 0.17f, 0.17f, 0.17f, 0.17f}, new boolean[] {false, true, true, true, false});
                pdf.heading("Monthly reconciliations");
                List<Map<String, Object>> rs = (List<Map<String, Object>>) a.get("reconciliations");
                if (rs.isEmpty()) pdf.para("None recorded in the period.");
                else pdf.table(List.of("Period end", "Bank", "Cashbook", "Client ledgers", "Difference", "Status", "Certified"),
                        rs.stream().map(x -> List.of(c(x.get("periodEnd")), r(x.get("bank")), r(x.get("cashbook")), r(x.get("clientLedgers")), r(x.get("difference")), c(x.get("status")), Boolean.TRUE.equals(x.get("certified")) ? "Yes" : "No")).toList(),
                        new float[] {0.14f, 0.15f, 0.15f, 0.15f, 0.13f, 0.14f, 0.14f}, new boolean[] {false, true, true, true, true, false, false});
                pdf.heading("Client ledger balances at " + to);
                List<Map<String, Object>> ls = (List<Map<String, Object>>) a.get("clientLedgers");
                pdf.table(List.of("Client", "Balance", "Last movement"), ls.stream().map(x -> List.of(c(x.get("client")), r(x.get("balance")), c(x.get("lastMovement")))).toList(),
                        new float[] {0.55f, 0.2f, 0.25f}, new boolean[] {false, true, false});
                pdf.heading("Trust cashbook");
                List<Map<String, Object>> cb = (List<Map<String, Object>>) a.get("cashbook");
                pdf.table(List.of("Date", "Type", "Client", "Description", "Debit", "Credit", "Balance"),
                        cb.stream().map(x -> List.of(c(x.get("date")), c(x.get("type")), c(x.get("client")), c(x.get("description")), r(x.get("debit")), r(x.get("credit")), r(x.get("balance")))).toList(),
                        new float[] {0.1f, 0.1f, 0.17f, 0.27f, 0.12f, 0.12f, 0.12f}, new boolean[] {false, false, false, false, true, true, true});
            }
            pdf.heading("Section 86(3) and 86(4) investments");
            List<Map<String, Object>> inv = (List<Map<String, Object>>) p.get("investments");
            if (inv.isEmpty()) pdf.para("No investments in the period.");
            else pdf.table(List.of("Section", "Client", "Institution", "Principal", "Interest earned", "Interest paid out", "Opened", "Closed"),
                    inv.stream().map(x -> List.of(c(x.get("section")), c(x.get("client")), c(x.get("institution")), r(x.get("principal")), r(x.get("interestEarned")), r(x.get("interestPaidOut")), c(x.get("openedOn")), c(x.get("closedOn")))).toList(),
                    new float[] {0.08f, 0.16f, 0.16f, 0.12f, 0.12f, 0.12f, 0.12f, 0.12f}, new boolean[] {false, false, false, true, true, true, false, false});
            pdf.para("Interest on s 86(3) investments: R " + r(p.get("interest86_3")) + " (payable to the Legal Practitioners' Fidelity Fund). Interest on s 86(4) investments: R " + r(p.get("interest86_4")) + " (for the clients concerned).");
            pdf.heading("Sign-off");
            pdf.para("Prepared by: ____________________   Date: __________\n\nReviewed by auditor: ____________________   Date: __________");
            return pdf.finish();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> invView(TrustInvestment i, Map<UUID, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("section", i.getSection());
        m.put("clientId", i.getClientId());
        m.put("client", i.getClientId() == null ? "Firm (surplus)" : names.getOrDefault(i.getClientId(), ""));
        m.put("institution", i.getInstitution());
        m.put("accountNumber", i.getAccountNumber());
        m.put("principal", i.getPrincipal());
        m.put("interestEarned", i.getInterestEarned());
        m.put("interestPaidOut", i.getInterestPaidOut());
        m.put("openedOn", i.getOpenedOn());
        m.put("closedOn", i.getClosedOn());
        m.put("notes", i.getNotes());
        return m;
    }

    private Map<UUID, String> clientNames() {
        Map<UUID, String> m = new HashMap<>();
        for (Client c : clients.findByTenantIdOrderByLastNameAsc(tid())) m.put(c.getId(), c.displayName());
        return m;
    }

    private static Map<String, Object> ex(String sev, String check, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", sev);
        m.put("check", check);
        m.put("detail", detail);
        return m;
    }

    static boolean out(String type) { return "withdrawal".equals(type) || "fee_transfer".equals(type); }
    private static String c(Object v) { return v == null ? "" : Csv.escape(v); }
    private static String r(Object v) { return v == null ? "" : new BigDecimal(String.valueOf(v)).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static String s(Object v) { if (v == null) return null; String x = String.valueOf(v).trim(); return x.isEmpty() ? null : x; }
    private static BigDecimal money(Object v) {
        try { return new BigDecimal(String.valueOf(v).replace(",", "").trim()); } catch (Exception e) { throw ApiException.badRequest("Amounts must be numbers."); }
    }
}
