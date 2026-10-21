package com.legalsuite.paylinks;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Invoice;
import com.legalsuite.domain.Tenant;
import com.legalsuite.payfast.PayFastSignature;
import com.legalsuite.payfast.PayFastSignature.Field;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.InvoiceRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.service.AuditService;
import com.legalsuite.service.FinanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client invoice payment links. Money goes to the firm's own PayFast merchant account, never to
 * Owlaw. Without the firm's live credentials, links run against PayFast's public sandbox merchant
 * so a firm can try the flow end to end with test cards. Feature flag: INVOICE_PAYMENTS_ENABLED.
 */
@Service
public class PayLinkService {
    private static final Logger log = LoggerFactory.getLogger(PayLinkService.class);
    // PayFast's published sandbox test merchant (https://developers.payfast.co.za/docs#step_1_form_fields).
    static final String SANDBOX_ID = "10000100", SANDBOX_KEY = "46f0cd694581a", SANDBOX_PASS = "jt7NOE43FZPn";

    private final FirmPaymentSettingsRepository settings;
    private final PaymentLinkRepository links;
    private final InvoiceRepository invoices;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final FinanceService finance;
    private final AuditService audit;
    private final SecretBox box;
    private final boolean enabled;
    private final String appUrl;
    private final String apiUrl;
    private final ItnValidator validator;

    public interface ItnValidator { boolean valid(String mode, List<Field> fields); }

    public PayLinkService(FirmPaymentSettingsRepository settings, PaymentLinkRepository links, InvoiceRepository invoices,
                          ClientRepository clients, TenantRepository tenants, FinanceService finance, AuditService audit, SecretBox box,
                          @Value("${legalsuite.invoice-payments.enabled:true}") boolean enabled,
                          @Value("${legalsuite.app-url:http://localhost:3000}") String appUrl,
                          @Value("${legalsuite.invoice-payments.notify-base:${TWILIO_PUBLIC_BASE_URL:}}") String apiUrl,
                          org.springframework.beans.factory.ObjectProvider<ItnValidator> validator) {
        this.settings = settings;
        this.links = links;
        this.invoices = invoices;
        this.clients = clients;
        this.tenants = tenants;
        this.finance = finance;
        this.audit = audit;
        this.box = box;
        this.enabled = enabled;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.apiUrl = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
        this.validator = validator.getIfAvailable(() -> this::validateWithPayFast);
    }

    // ---- Firm settings ----------------------------------------------------------------------------

    public Map<String, Object> getSettings() {
        FirmPaymentSettings s = settings.findById(TenantContext.requireTenant()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("featureEnabled", enabled);
        m.put("enabled", s == null || s.isEnabled());
        m.put("mode", s == null ? "sandbox" : s.getMode());
        m.put("merchantId", s == null ? null : s.getMerchantId());
        m.put("merchantKeySet", s != null && s.getMerchantKeyEnc() != null);
        m.put("passphraseSet", s != null && s.getPassphraseEnc() != null);
        m.put("canStoreLive", box.ready());
        return m;
    }

    @Transactional
    public Map<String, Object> saveSettings(Map<String, Object> b) {
        Roles.requireTenantAdmin("Only a firm owner, director or partner can change payment settings.");
        UUID tid = TenantContext.requireTenant();
        FirmPaymentSettings s = settings.findById(tid).orElseGet(() -> { FirmPaymentSettings n = new FirmPaymentSettings(); n.setTenantId(tid); return n; });
        if (b.get("enabled") != null) s.setEnabled(Boolean.parseBoolean(String.valueOf(b.get("enabled"))));
        String mode = b.get("mode") == null ? s.getMode() : String.valueOf(b.get("mode"));
        if (!List.of("sandbox", "live").contains(mode)) throw ApiException.badRequest("Mode is sandbox or live.");
        if (b.get("merchantId") != null) {
            String id = String.valueOf(b.get("merchantId")).trim();
            if (!id.isEmpty() && !id.matches("\\d{5,12}")) throw ApiException.badRequest("PayFast merchant ID is digits only.");
            s.setMerchantId(id.isEmpty() ? null : id);
        }
        if (b.get("merchantKey") != null && !String.valueOf(b.get("merchantKey")).isBlank()) {
            if (!box.ready()) throw ApiException.badRequest("Owlaw cannot store live keys yet (server encryption key missing). Use sandbox for now.");
            s.setMerchantKeyEnc(box.seal(String.valueOf(b.get("merchantKey")).trim()));
        }
        if (b.get("passphrase") != null && !String.valueOf(b.get("passphrase")).isBlank()) {
            if (!box.ready()) throw ApiException.badRequest("Owlaw cannot store live keys yet (server encryption key missing). Use sandbox for now.");
            s.setPassphraseEnc(box.seal(String.valueOf(b.get("passphrase")).trim()));
        }
        if ("live".equals(mode) && (s.getMerchantId() == null || s.getMerchantKeyEnc() == null || s.getPassphraseEnc() == null)) {
            throw ApiException.badRequest("Live mode needs your PayFast merchant ID, merchant key and passphrase (PayFast, Settings, Developer settings).");
        }
        s.setMode(mode);
        s.setUpdatedAt(Instant.now());
        settings.save(s);
        audit.record("payments.settings", "tenant", tid.toString(), "mode " + mode + ", enabled " + s.isEnabled());
        return getSettings();
    }

    // ---- Links ------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> create(UUID invoiceId) {
        if (!enabled) throw ApiException.badRequest("Online invoice payments are switched off on this server.");
        UUID tid = TenantContext.requireTenant();
        FirmPaymentSettings s = settings.findById(tid).orElse(null);
        if (s != null && !s.isEnabled()) throw ApiException.badRequest("Online payments are switched off for your firm. Turn them on under Settings, Online payments.");
        Invoice inv = invoices.findByIdAndTenantId(invoiceId, tid).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if ("draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) throw ApiException.badRequest("Send the invoice first. Draft and void invoices cannot be paid online.");
        BigDecimal due = due(inv);
        if (due.signum() <= 0) throw ApiException.badRequest("This invoice has no balance due.");
        for (PaymentLink old : links.findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(tid, invoiceId)) {
            if ("open".equals(old.getStatus())) { old.setStatus("cancelled"); links.save(old); }
        }
        PaymentLink l = new PaymentLink();
        l.setTenantId(tid);
        l.setInvoiceId(invoiceId);
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        l.setToken(HexFormat.of().formatHex(raw));
        l.setAmount(due);
        l.setMode(s == null ? "sandbox" : s.getMode());
        l.setCreatedBy(TenantContext.getUserId());
        l.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        links.save(l);
        audit.record("invoice.pay_link", "invoice", invoiceId.toString(), inv.getInvoiceNumber() + " " + due + " " + l.getMode());
        return linkView(l, inv);
    }

    /** Public: what the /pay/{token} page shows, plus the signed PayFast form. */
    public Map<String, Object> publicView(String token) {
        PaymentLink l = links.findByToken(token).orElseThrow(() -> ApiException.notFound("This payment link is not valid."));
        Invoice inv = invoices.findById(l.getInvoiceId()).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        Tenant firm = tenants.findById(l.getTenantId()).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firm", firm.getFirmName());
        m.put("invoiceNumber", inv.getInvoiceNumber());
        m.put("amount", l.getAmount());
        m.put("status", l.getExpiresAt().isBefore(Instant.now()) && "open".equals(l.getStatus()) ? "expired" : l.getStatus());
        m.put("mode", l.getMode());
        if (!"open".equals(m.get("status"))) return m;
        if (java.util.Set.of("write_off", "void", "cancelled").contains(inv.getStatus())) { m.put("status", "closed"); return m; }
        BigDecimal due = due(inv);
        if (due.signum() <= 0) { m.put("status", "paid"); return m; }
        Creds c = creds(l);
        Client client = inv.getClientId() == null ? null : clients.findById(inv.getClientId()).orElse(null);
        List<Field> f = new ArrayList<>();
        f.add(new Field("merchant_id", c.id));
        f.add(new Field("merchant_key", c.key));
        f.add(new Field("return_url", appUrl + "/pay/" + token + "?done=1"));
        f.add(new Field("cancel_url", appUrl + "/pay/" + token + "?cancelled=1"));
        f.add(new Field("notify_url", notifyBase() + "/api/v1/public/pay/" + token + "/itn"));
        if (client != null && client.getFirstName() != null) f.add(new Field("name_first", client.getFirstName()));
        if (client != null && client.getLastName() != null) f.add(new Field("name_last", client.getLastName()));
        if (client != null && client.getEmail() != null) f.add(new Field("email_address", client.getEmail()));
        f.add(new Field("m_payment_id", l.getId().toString()));
        f.add(new Field("amount", l.getAmount().min(due).setScale(2, RoundingMode.HALF_UP).toPlainString()));
        f.add(new Field("item_name", ("Invoice " + inv.getInvoiceNumber()).replaceAll("[^A-Za-z0-9 \\-]", "")));
        String sig = PayFastSignature.sign(f, c.pass);
        Map<String, String> fields = new LinkedHashMap<>();
        for (Field x : f) fields.put(x.name(), x.value());
        fields.put("signature", sig);
        m.put("action", "live".equals(l.getMode()) ? "https://www.payfast.co.za/eng/process" : "https://sandbox.payfast.co.za/eng/process");
        m.put("fields", fields);
        return m;
    }

    /** PayFast ITN. Checks signature, amount and PayFast's own validation before booking the payment. */
    @Transactional
    public boolean itn(String token, List<Field> posted) {
        PaymentLink l = links.findByToken(token).orElse(null);
        if (l == null) return false;
        if ("paid".equals(l.getStatus())) return true;
        Creds c = creds(l);
        List<Field> unsigned = posted.stream().filter(x -> !"signature".equals(x.name())).toList();
        if (!PayFastSignature.matches(unsigned, c.pass, PayFastSignature.value(posted, "signature"))) {
            log.warn("Pay link ITN signature mismatch for link {}", l.getId());
            return false;
        }
        if (!l.getId().toString().equals(PayFastSignature.value(posted, "m_payment_id"))) return false;
        if (!c.id.equals(PayFastSignature.value(posted, "merchant_id"))) return false;
        if (!"COMPLETE".equals(PayFastSignature.value(posted, "payment_status"))) return true;
        BigDecimal gross;
        try { gross = new BigDecimal(PayFastSignature.value(posted, "amount_gross")); } catch (Exception e) { return false; }
        if (gross.compareTo(l.getAmount()) > 0) return false;
        if (!validator.valid(l.getMode(), unsigned)) { log.warn("Pay link ITN not validated by PayFast for link {}", l.getId()); return false; }
        Invoice inv = invoices.findById(l.getInvoiceId()).orElseThrow();
        UUID prevT = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(l.getTenantId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", gross.min(due(inv)).toPlainString());
            body.put("method", "card");
            body.put("reference", "PayFast " + PayFastSignature.value(posted, "pf_payment_id"));
            body.put("note", "Paid online" + ("sandbox".equals(l.getMode()) ? " (PayFast sandbox test)" : ""));
            finance.recordPayment(inv.getId(), body);
        } finally {
            TenantContext.setTenantId(prevT);
        }
        l.setStatus("paid");
        l.setPaidAt(Instant.now());
        l.setPfPaymentId(PayFastSignature.value(posted, "pf_payment_id"));
        links.save(l);
        audit.recordFor(l.getTenantId(), "invoice.paid_online", "invoice", inv.getId().toString(), inv.getInvoiceNumber() + " " + gross);
        return true;
    }

    private boolean validateWithPayFast(String mode, List<Field> unsigned) {
        String url = "live".equals(mode) ? "https://www.payfast.co.za/eng/query/validate" : "https://sandbox.payfast.co.za/eng/query/validate";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(PayFastSignature.formBody(unsigned))).build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && r.body() != null && r.body().trim().startsWith("VALID");
        } catch (Exception e) {
            return false;
        }
    }

    private record Creds(String id, String key, String pass) {}

    private Creds creds(PaymentLink l) {
        if (!"live".equals(l.getMode())) return new Creds(SANDBOX_ID, SANDBOX_KEY, SANDBOX_PASS);
        FirmPaymentSettings s = settings.findById(l.getTenantId()).orElseThrow(() -> ApiException.badRequest("Payment settings missing"));
        String key = box.open(s.getMerchantKeyEnc()), pass = box.open(s.getPassphraseEnc());
        if (key == null || pass == null) throw ApiException.badRequest("This firm's PayFast details cannot be read. Ask the firm to re-enter them.");
        return new Creds(s.getMerchantId(), key, pass);
    }

    private String notifyBase() {
        return apiUrl.isEmpty() ? appUrl : apiUrl;
    }

    private static BigDecimal due(Invoice inv) {
        BigDecimal t = inv.getTotal() == null ? BigDecimal.ZERO : inv.getTotal();
        BigDecimal p = inv.getAmountPaid() == null ? BigDecimal.ZERO : inv.getAmountPaid();
        BigDecimal w = inv.getWriteOffAmount() == null ? BigDecimal.ZERO : inv.getWriteOffAmount();
        return t.subtract(p).subtract(w).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> linkView(PaymentLink l, Invoice inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", l.getToken());
        m.put("url", appUrl + "/pay/" + l.getToken());
        m.put("amount", l.getAmount());
        m.put("mode", l.getMode());
        m.put("expiresAt", l.getExpiresAt());
        m.put("invoiceNumber", inv.getInvoiceNumber());
        Client c = inv.getClientId() == null ? null : clients.findById(inv.getClientId()).orElse(null);
        m.put("clientName", c == null ? null : c.displayName());
        m.put("clientEmail", c == null ? null : c.getEmail());
        m.put("clientPhone", c == null ? null : (c.getMobile() != null ? c.getMobile() : c.getPhone()));
        return m;
    }

    // ---- Statements -------------------------------------------------------------------------------

    public Map<String, Object> statement(UUID clientId) {
        UUID tid = TenantContext.requireTenant();
        Client c = clients.findByIdAndTenantId(clientId, tid).orElseThrow(() -> ApiException.notFound("Client not found"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Invoice inv : invoices.findByTenantIdOrderByDateIssuedDesc(tid)) {
            if (!clientId.equals(inv.getClientId()) || "draft".equals(inv.getStatus()) || "void".equals(inv.getStatus())) continue;
            BigDecimal d = due(inv);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("invoiceId", inv.getId());
            r.put("invoiceNumber", inv.getInvoiceNumber());
            r.put("dateIssued", inv.getDateIssued());
            r.put("dateDue", inv.getDateDue());
            r.put("total", inv.getTotal());
            r.put("paid", inv.getAmountPaid());
            r.put("balanceDue", d);
            r.put("status", inv.getStatus());
            rows.add(r);
            total = total.add(d);
        }
        Tenant firm = tenants.findById(tid).orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firm", firm.getFirmName());
        m.put("clientName", c.displayName());
        m.put("clientEmail", c.getEmail());
        m.put("clientPhone", c.getMobile() != null ? c.getMobile() : c.getPhone());
        m.put("date", java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")));
        m.put("invoices", rows);
        m.put("totalDue", total);
        return m;
    }

    @SuppressWarnings("unchecked")
    public byte[] statementPdf(UUID clientId) {
        Map<String, Object> s = statement(clientId);
        try (com.legalsuite.reports.SimplePdf pdf = new com.legalsuite.reports.SimplePdf(s.get("firm") + " | Statement for " + s.get("clientName") + " | " + s.get("date"))) {
            pdf.title("Statement of account");
            pdf.para(s.get("firm") + "\nClient: " + s.get("clientName") + "\nDate: " + s.get("date"));
            List<Map<String, Object>> rows = (List<Map<String, Object>>) s.get("invoices");
            pdf.table(List.of("Invoice", "Issued", "Due", "Total", "Paid", "Balance"),
                    rows.stream().map(r -> List.of(String.valueOf(r.get("invoiceNumber")), String.valueOf(r.get("dateIssued")), r.get("dateDue") == null ? "" : String.valueOf(r.get("dateDue")),
                            m2(r.get("total")), m2(r.get("paid")), m2(r.get("balanceDue")))).toList(),
                    new float[] {0.2f, 0.16f, 0.16f, 0.16f, 0.16f, 0.16f}, new boolean[] {false, false, false, true, true, true});
            pdf.heading("Total due: R " + m2(s.get("totalDue")));
            pdf.para("Pay by EFT using your invoice number as the reference, or use the online payment link your attorney sends you.");
            audit.record("client.statement", "client", clientId.toString(), "statement PDF");
            return pdf.finish();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String m2(Object v) {
        return v == null ? "0.00" : new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
