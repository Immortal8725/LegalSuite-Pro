package com.legalsuite.payfast;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.MinutesInvoice;
import com.legalsuite.domain.ProductCheckout;
import com.legalsuite.domain.ProductPayment;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.ProductCheckoutRepository;
import com.legalsuite.repo.ProductPaymentRepository;
import com.legalsuite.service.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual pilot trigger for the phone-minute invoice and the tokenised adhoc charge.
 * There is no scheduler. A director runs it from product billing.
 */
@Service
public class MinutesChargeJob {
    private final CallRecordRepository calls;
    private final MinutesInvoiceRepository invoices;
    private final BillingAccountRepository accounts;
    private final ProductCheckoutRepository checkouts;
    private final ProductPaymentRepository payments;
    private final PayFastAdhocGateway gateway;
    private final PayFastProperties props;
    private final AuditService audit;
    private final Clock clock;

    public MinutesChargeJob(
            CallRecordRepository calls,
            MinutesInvoiceRepository invoices,
            BillingAccountRepository accounts,
            ProductCheckoutRepository checkouts,
            ProductPaymentRepository payments,
            PayFastAdhocGateway gateway,
            PayFastProperties props,
            AuditService audit,
            Clock clock) {
        this.calls = calls;
        this.invoices = invoices;
        this.accounts = accounts;
        this.checkouts = checkouts;
        this.payments = payments;
        this.gateway = gateway;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> charge(String periodText) {
        ProductBillingAccess.requireManager();
        UUID tenantId = TenantContext.requireTenant();
        YearMonth period = parsePeriod(periodText);
        MinutesInvoice existing = invoices.findByTenantIdAndPeriod(tenantId, period.toString()).orElse(null);
        if (existing != null && "charged".equals(existing.getStatus())) return view(existing);
        Usage usage = usage(tenantId, period);
        MinutesInvoice invoice = existing == null ? new MinutesInvoice() : existing;
        invoice.setTenantId(tenantId);
        invoice.setPeriod(period.toString());
        invoice.setMinutes(usage.minutes());
        invoice.setAmountCents(usage.cents());
        invoice.setCreatedAt(invoice.getCreatedAt() == null ? Instant.now(clock) : invoice.getCreatedAt());
        if (usage.cents() <= 0) {
            invoice.setStatus("nothing_due");
            invoice.setNote(usage.minutes() == 0
                    ? "No public-network minutes in this period."
                    : "Minutes were recorded at zero cost. Set the per-minute rate before charging.");
            invoices.save(invoice);
            return view(invoice);
        }
        BillingAccount account = accounts.findByTenantId(tenantId).orElse(null);
        String token = account == null ? "" : blank(account.getPayfastToken());
        if (token.isBlank()) {
            invoice.setStatus("issued");
            invoice.setNote("Invoice recorded. No PayFast token is on file yet, so the card was not charged.");
            invoices.save(invoice);
            audit.record("billing.minutes", "billing", period.toString(), "issued " + usage.cents());
            return view(invoice);
        }
        if (!props.merchantPresent()) {
            invoice.setStatus("issued");
            invoice.setNote("Invoice recorded. PayFast merchant id and key are not set, so no charge was sent.");
            invoices.save(invoice);
            return view(invoice);
        }
        String mPaymentId = "min-" + UUID.randomUUID().toString().replace("-", "");
        invoice.setMerchantPaymentId(mPaymentId);
        ProductCheckout checkout = new ProductCheckout();
        checkout.setTenantId(tenantId);
        checkout.setMerchantPaymentId(mPaymentId);
        checkout.setKind("minutes");
        checkout.setAmountCents(usage.cents());
        checkout.setPeriod(period.toString());
        checkout.setStatus("pending");
        checkouts.save(checkout);
        PayFastAdhocGateway.AdhocReceipt receipt = gateway.charge(new PayFastAdhocGateway.AdhocCharge(
                token,
                usage.cents(),
                "LegalSuite phone minutes",
                period + " public-network minutes",
                mPaymentId));
        if (!receipt.accepted()) {
            invoice.setStatus("failed");
            invoice.setNote(trim(receipt.message()));
            checkout.setStatus("failed");
            checkouts.save(checkout);
            invoices.save(invoice);
            audit.record("billing.minutes", "billing", mPaymentId, "failed " + usage.cents());
            return view(invoice);
        }
        invoice.setStatus("charged");
        invoice.setPfPaymentId(blank(receipt.pfPaymentId()));
        invoice.setChargedAt(Instant.now(clock));
        invoice.setNote(trim(receipt.message()));
        checkout.setStatus("complete");
        checkouts.save(checkout);
        invoices.save(invoice);
        if (!blank(receipt.pfPaymentId()).isBlank() && payments.findByPfPaymentId(receipt.pfPaymentId()).isEmpty()) {
            ProductPayment payment = new ProductPayment();
            payment.setTenantId(tenantId);
            payment.setKind("minutes");
            payment.setMerchantPaymentId(mPaymentId);
            payment.setPfPaymentId(receipt.pfPaymentId());
            payment.setToken(token);
            payment.setAmountCents(usage.cents());
            payment.setPaymentStatus("complete");
            payment.setPeriod(period.toString());
            payments.save(payment);
        }
        if (account != null) {
            account.setLastPaymentAt(Instant.now(clock));
            account.setLastAmountCents(usage.cents());
            account.setLastMerchantPaymentId(mPaymentId);
            if (!blank(receipt.pfPaymentId()).isBlank()) account.setLastPfPaymentId(receipt.pfPaymentId());
            account.setUpdatedAt(Instant.now(clock));
            accounts.save(account);
        }
        audit.record("billing.minutes", "billing", mPaymentId, "charged " + usage.cents());
        return view(invoice);
    }

    private Usage usage(UUID tenantId, YearMonth month) {
        var from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal total = BigDecimal.ZERO;
        int minutes = 0;
        for (CallRecord call : calls.findByTenantIdOrderByStartedAtDesc(tenantId)) {
            if (call.getStartedAt() == null || call.getStartedAt().isBefore(from) || !call.getStartedAt().isBefore(to)) continue;
            if (call.getCallType() == null || !call.getCallType().startsWith("pstn")) continue;
            total = total.add(call.getTotalCost() == null ? BigDecimal.ZERO : call.getTotalCost());
            minutes += Math.max(0, call.getDurationSeconds()) / 60;
        }
        return new Usage(minutes, PayFastMoney.randsToCents(total));
    }

    private YearMonth parsePeriod(String periodText) {
        if (periodText == null || periodText.isBlank()) return YearMonth.now(clock);
        try {
            return YearMonth.parse(periodText.trim());
        } catch (DateTimeParseException ex) {
            throw com.legalsuite.common.ApiException.badRequest("Period must look like 2026-09");
        }
    }

    public static Map<String, Object> view(MinutesInvoice invoice) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", invoice.getId());
        m.put("period", invoice.getPeriod());
        m.put("minutes", invoice.getMinutes());
        m.put("amountCents", invoice.getAmountCents());
        m.put("amount", PayFastMoney.formatRands(invoice.getAmountCents()));
        m.put("status", invoice.getStatus());
        m.put("mPaymentId", invoice.getMerchantPaymentId());
        m.put("pfPaymentId", invoice.getPfPaymentId());
        m.put("note", invoice.getNote());
        m.put("chargedAt", invoice.getChargedAt());
        return m;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trim(String value) {
        String text = blank(value);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private record Usage(int minutes, long cents) {}
}
