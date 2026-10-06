package com.legalsuite.payfast;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.ProductCheckout;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.ProductCheckoutRepository;
import com.legalsuite.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayFastCheckoutService {
    private final PayFastProperties props;
    private final BillingAccountRepository accounts;
    private final ProductCheckoutRepository checkouts;
    private final AppUserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public PayFastCheckoutService(
            PayFastProperties props,
            BillingAccountRepository accounts,
            ProductCheckoutRepository checkouts,
            AppUserRepository users,
            AuditService audit,
            Clock clock) {
        this.props = props;
        this.accounts = accounts;
        this.checkouts = checkouts;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> seatCheckout(int seats) {
        ProductBillingAccess.requireManager();
        requireReady();
        UUID tenantId = TenantContext.requireTenant();
        Buyer buyer = buyer();
        String mPaymentId = "seat-" + UUID.randomUUID().toString().replace("-", "");
        LocalDate billingDate = LocalDate.now(clock.withZone(ZoneId.of("Africa/Johannesburg")));
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.seat(
                props, tenantId, buyer.first(), buyer.last(), buyer.email(), mPaymentId, seats, billingDate);
        saveCheckout(tenantId, mPaymentId, "seat", seats, built.amountCents(), null);
        markPending(tenantId, seats);
        audit.record("billing.checkout", "billing", mPaymentId, "seat " + seats + " " + built.amountCents());
        return view(built);
    }

    @Transactional
    public Map<String, Object> tokenCheckout() {
        ProductBillingAccess.requireManager();
        requireReady();
        UUID tenantId = TenantContext.requireTenant();
        Buyer buyer = buyer();
        String mPaymentId = "token-" + UUID.randomUUID().toString().replace("-", "");
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.token(
                props, tenantId, buyer.first(), buyer.last(), buyer.email(), mPaymentId);
        saveCheckout(tenantId, mPaymentId, "token", 0, 0L, null);
        audit.record("billing.token", "billing", mPaymentId, "tokenisation");
        return view(built);
    }

    private void requireReady() {
        if (!props.readyForCheckout()) {
            throw ApiException.badRequest(
                    "PayFast is not configured. Set PAYFAST_MERCHANT_ID, PAYFAST_MERCHANT_KEY, PAYFAST_RETURN_URL, PAYFAST_CANCEL_URL, and PAYFAST_NOTIFY_URL. Use PAYFAST_ENV=sandbox until the live merchant is approved. Set PAYFAST_PASSPHRASE when the merchant account has one.");
        }
    }

    private void saveCheckout(UUID tenantId, String mPaymentId, String kind, int seats, long cents, String period) {
        ProductCheckout row = new ProductCheckout();
        row.setTenantId(tenantId);
        row.setMerchantPaymentId(mPaymentId);
        row.setKind(kind);
        row.setSeatCount(seats);
        row.setAmountCents(cents);
        row.setPeriod(period);
        row.setStatus("pending");
        checkouts.save(row);
    }

    private void markPending(UUID tenantId, int seats) {
        BillingAccount account = accounts.findByTenantId(tenantId).orElseGet(() -> {
            BillingAccount created = new BillingAccount();
            created.setTenantId(tenantId);
            created.setEnvironment(props.environment());
            return created;
        });
        account.setSeatCount(seats);
        if (!"active".equals(account.getSeatStatus())) {
            account.setSeatStatus("pending");
        }
        account.setEnvironment(props.environment());
        account.setUpdatedAt(Instant.now(clock));
        accounts.save(account);
    }

    private Buyer buyer() {
        UUID userId = TenantContext.getUserId();
        AppUser user = userId == null ? null : users.findById(userId).orElse(null);
        String first = user == null || user.getFirstName() == null ? "Firm" : user.getFirstName();
        String last = user == null || user.getLastName() == null ? "Admin" : user.getLastName();
        String email = user != null && user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : TenantContext.getEmail();
        return new Buyer(first, last, email == null ? "" : email);
    }

    private Map<String, Object> view(PayFastCheckoutForm.Built built) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", built.action());
        m.put("method", "POST");
        m.put("environment", built.environment());
        m.put("amountCents", built.amountCents());
        m.put("amount", built.amount());
        m.put("subscriptionType", built.subscriptionType());
        m.put("frequency", built.frequency());
        m.put("mPaymentId", built.mPaymentId());
        m.put("fields", built.fields().stream().map(field -> Map.of("name", field.name(), "value", field.value())).toList());
        return m;
    }

    private record Buyer(String first, String last, String email) {}
}
