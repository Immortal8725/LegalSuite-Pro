package com.legalsuite.payfast;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.MinutesInvoice;
import com.legalsuite.domain.ProductCheckout;
import com.legalsuite.domain.ProductPayment;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.ProductCheckoutRepository;
import com.legalsuite.repo.ProductPaymentRepository;
import com.legalsuite.service.AuditService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayFastItnService {
    private static final Logger log = LoggerFactory.getLogger(PayFastItnService.class);
    private static final ZoneId JOHANNESBURG = ZoneId.of("Africa/Johannesburg");

    private final PayFastProperties props;
    private final BillingAccountRepository accounts;
    private final ProductPaymentRepository payments;
    private final ProductCheckoutRepository checkouts;
    private final MinutesInvoiceRepository minutes;
    private final PayFastServerConfirm confirm;
    private final AuditService audit;

    public PayFastItnService(
            PayFastProperties props,
            BillingAccountRepository accounts,
            ProductPaymentRepository payments,
            ProductCheckoutRepository checkouts,
            MinutesInvoiceRepository minutes,
            PayFastServerConfirm confirm,
            AuditService audit) {
        this.props = props;
        this.accounts = accounts;
        this.payments = payments;
        this.checkouts = checkouts;
        this.minutes = minutes;
        this.confirm = confirm;
        this.audit = audit;
    }

    public record ItnOutcome(int httpStatus, String reason) {
        public static ItnOutcome ok(String reason) {
            return new ItnOutcome(200, reason);
        }

        public static ItnOutcome reject(String reason) {
            return new ItnOutcome(400, reason);
        }
    }

    @Transactional
    public ItnOutcome handle(List<PayFastSignature.Field> posted) {
        if (posted == null || posted.isEmpty()) return ItnOutcome.reject("empty");
        String postedSignature = PayFastSignature.value(posted, "signature");
        if (!PayFastSignature.matches(posted, props.getPassphrase(), postedSignature)) {
            log.info("PayFast ITN rejected: signature");
            return ItnOutcome.reject("signature");
        }
        String pfPaymentId = PayFastSignature.value(posted, "pf_payment_id");
        if (!pfPaymentId.isBlank() && payments.findByPfPaymentId(pfPaymentId).isPresent()) {
            return ItnOutcome.ok("duplicate");
        }
        if (!confirm.confirmed(posted)) {
            log.info("PayFast ITN rejected: server confirm");
            return ItnOutcome.reject("confirm");
        }

        String mPaymentId = PayFastSignature.value(posted, "m_payment_id");
        Optional<ProductCheckout> checkout = mPaymentId.isBlank()
                ? Optional.empty()
                : checkouts.findByMerchantPaymentId(mPaymentId);
        UUID tenantId = checkout.map(ProductCheckout::getTenantId)
                .orElseGet(() -> parseUuid(PayFastSignature.value(posted, "custom_str1")));
        if (tenantId == null) return ItnOutcome.reject("unknown");

        String kind = kindOf(mPaymentId, checkout, PayFastSignature.value(posted, "custom_str2"));
        long gross;
        try {
            gross = PayFastMoney.parseRands(firstAmount(posted));
        } catch (IllegalArgumentException ex) {
            return ItnOutcome.reject("amount");
        }
        long expected = expectedCents(kind, checkout, mPaymentId, posted);
        String paymentStatus = PayFastSignature.value(posted, "payment_status").toUpperCase(Locale.ROOT);
        String token = PayFastSignature.value(posted, "token");

        if (gross != expected) {
            savePayment(tenantId, kind, mPaymentId, pfPaymentId, "", gross, "rejected", periodOf(checkout));
            log.info("PayFast ITN amount mismatch for {}", mPaymentId);
            return ItnOutcome.ok("amount_mismatch");
        }

        if (!"COMPLETE".equals(paymentStatus)) {
            savePayment(tenantId, kind, mPaymentId, pfPaymentId, "", gross, paymentStatus.toLowerCase(Locale.ROOT), periodOf(checkout));
            checkout.ifPresent(row -> {
                row.setStatus("FAILED".equals(paymentStatus) ? "failed" : "pending");
                checkouts.save(row);
            });
            if ("FAILED".equals(paymentStatus) && "seat".equals(kind)) {
                BillingAccount account = load(tenantId);
                if (!"active".equals(account.getSeatStatus())) account.setSeatStatus("past_due");
                account.setUpdatedAt(Instant.now());
                accounts.save(account);
            }
            audit(tenantId, "billing.itn", mPaymentId, paymentStatus + " " + gross);
            return ItnOutcome.ok(paymentStatus.toLowerCase(Locale.ROOT));
        }

        savePayment(tenantId, kind, mPaymentId, pfPaymentId, token, gross, "complete", periodOf(checkout));
        applyComplete(tenantId, kind, checkout, posted, mPaymentId, pfPaymentId, token, gross);
        checkout.ifPresent(row -> {
            row.setStatus("complete");
            checkouts.save(row);
        });
        audit(tenantId, "billing.itn", mPaymentId, "COMPLETE " + gross);
        return ItnOutcome.ok("complete");
    }

    private void applyComplete(
            UUID tenantId,
            String kind,
            Optional<ProductCheckout> checkout,
            List<PayFastSignature.Field> posted,
            String mPaymentId,
            String pfPaymentId,
            String token,
            long gross) {
        BillingAccount account = load(tenantId);
        account.setEnvironment(props.environment());
        account.setLastPaymentAt(Instant.now());
        account.setLastAmountCents(gross);
        if (!pfPaymentId.isBlank()) account.setLastPfPaymentId(pfPaymentId);
        if (!mPaymentId.isBlank()) account.setLastMerchantPaymentId(mPaymentId);
        if (!token.isBlank()) account.setPayfastToken(token);
        account.setUpdatedAt(Instant.now());
        if ("minutes".equals(kind)) {
            accounts.save(account);
            markMinutesCharged(mPaymentId, pfPaymentId);
            return;
        }
        if ("token".equals(kind)) {
            accounts.save(account);
            return;
        }
        int seats = checkout.map(ProductCheckout::getSeatCount).orElseGet(() -> parseSeats(PayFastSignature.value(posted, "custom_int1"), 1));
        account.setSeatCount(seats);
        account.setSeatStatus("active");
        account.setSeatActiveUntil(extend(account.getSeatActiveUntil()));
        accounts.save(account);
    }

    private void markMinutesCharged(String mPaymentId, String pfPaymentId) {
        if (mPaymentId == null || mPaymentId.isBlank()) return;
        minutes.findByMerchantPaymentId(mPaymentId).ifPresent(row -> {
            row.setStatus("charged");
            row.setPfPaymentId(pfPaymentId);
            row.setChargedAt(Instant.now());
            row.setNote("Charged from the PayFast ITN");
            minutes.save(row);
        });
    }

    private long expectedCents(
            String kind,
            Optional<ProductCheckout> checkout,
            String mPaymentId,
            List<PayFastSignature.Field> posted) {
        if (checkout.isPresent()) return checkout.get().getAmountCents();
        if ("minutes".equals(kind)) {
            return minutes.findByMerchantPaymentId(mPaymentId).map(MinutesInvoice::getAmountCents).orElse(-1L);
        }
        if ("token".equals(kind)) return 0L;
        int seats = parseSeats(PayFastSignature.value(posted, "custom_int1"), 1);
        return PayFastMoney.seatsCents(seats);
    }

    private BillingAccount load(UUID tenantId) {
        return accounts.findByTenantId(tenantId).orElseGet(() -> {
            BillingAccount created = new BillingAccount();
            created.setTenantId(tenantId);
            created.setEnvironment(props.environment());
            created.setSeatStatus("inactive");
            return created;
        });
    }

    private void savePayment(
            UUID tenantId,
            String kind,
            String mPaymentId,
            String pfPaymentId,
            String token,
            long amountCents,
            String status,
            String period) {
        ProductPayment payment = new ProductPayment();
        payment.setTenantId(tenantId);
        payment.setKind(kind);
        payment.setMerchantPaymentId(mPaymentId);
        payment.setPfPaymentId(pfPaymentId.isBlank() ? null : pfPaymentId);
        payment.setToken(token.isBlank() ? null : token);
        payment.setAmountCents(amountCents);
        payment.setPaymentStatus(status);
        payment.setPeriod(period);
        payments.save(payment);
    }

    private void audit(UUID tenantId, String action, String entityId, String detail) {
        UUID previous = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            audit.record(action, "billing", entityId, detail);
        } finally {
            TenantContext.setTenantId(previous);
        }
    }

    private static String kindOf(String mPaymentId, Optional<ProductCheckout> checkout, String custom) {
        if (mPaymentId != null && mPaymentId.startsWith("min-")) return "minutes";
        if (checkout.isPresent() && checkout.get().getKind() != null) return checkout.get().getKind();
        if ("minutes".equals(custom) || "token".equals(custom) || "seat".equals(custom)) return custom;
        return "seat";
    }

    private static String periodOf(Optional<ProductCheckout> checkout) {
        return checkout.map(ProductCheckout::getPeriod).orElse(null);
    }

    private static String firstAmount(List<PayFastSignature.Field> posted) {
        String gross = PayFastSignature.value(posted, "amount_gross");
        if (!gross.isBlank()) return gross;
        return PayFastSignature.value(posted, "amount");
    }

    private static Instant extend(Instant current) {
        ZonedDateTime now = ZonedDateTime.now(JOHANNESBURG);
        ZonedDateTime base = current == null ? now : current.atZone(JOHANNESBURG);
        if (base.isBefore(now)) base = now;
        return base.plusMonths(1).toInstant();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int parseSeats(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            String head = raw.trim();
            int dot = head.indexOf('.');
            if (dot >= 0) head = head.substring(0, dot);
            int seats = Integer.parseInt(head);
            return seats < 1 ? fallback : seats;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
