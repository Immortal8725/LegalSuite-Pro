package com.legalsuite.payfast;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.ProductPayment;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.ProductPaymentRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProductBillingService {
    private final PayFastProperties props;
    private final BillingAccountRepository accounts;
    private final ProductPaymentRepository payments;
    private final MinutesInvoiceRepository minutes;

    public ProductBillingService(
            PayFastProperties props,
            BillingAccountRepository accounts,
            ProductPaymentRepository payments,
            MinutesInvoiceRepository minutes) {
        this.props = props;
        this.accounts = accounts;
        this.payments = payments;
        this.minutes = minutes;
    }

    public Map<String, Object> status() {
        ProductBillingAccess.requireStaff();
        var tenantId = TenantContext.requireTenant();
        BillingAccount account = accounts.findByTenantId(tenantId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "payfast");
        m.put("environment", props.environment());
        m.put("merchantConfigured", props.merchantPresent());
        m.put("checkoutReady", props.readyForCheckout());
        m.put("missing", props.missingCheckoutSettings());
        m.put("passphraseSet", props.getPassphrase() != null && !props.getPassphrase().isBlank());
        m.put("canManage", ProductBillingAccess.canManage());
        m.put("seatPriceCents", PayFastMoney.LIGHT_SEAT_CENTS);
        m.put("seatPrice", PayFastMoney.formatRands(PayFastMoney.LIGHT_SEAT_CENTS));
        m.put("seatCount", account == null ? 0 : account.getSeatCount());
        m.put("seatStatus", account == null ? "inactive" : account.getSeatStatus());
        m.put("seatActiveUntil", account == null ? null : account.getSeatActiveUntil());
        m.put("lastPaymentAt", account == null ? null : account.getLastPaymentAt());
        m.put("lastAmountCents", account == null ? null : account.getLastAmountCents());
        m.put("lastAmount", account == null || account.getLastAmountCents() == null
                ? null
                : PayFastMoney.formatRands(account.getLastAmountCents()));
        m.put("lastPfPaymentId", account == null ? null : account.getLastPfPaymentId());
        m.put("lastMPaymentId", account == null ? null : account.getLastMerchantPaymentId());
        String token = account == null ? "" : (account.getPayfastToken() == null ? "" : account.getPayfastToken());
        m.put("tokenPresent", !token.isBlank());
        m.put("tokenHint", token.length() < 4 ? null : token.substring(token.length() - 4));
        m.put("payments", payments.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().limit(20).map(this::payment).toList());
        m.put("minutes", minutes.findByTenantIdOrderByPeriodDesc(tenantId).stream().limit(12).map(MinutesChargeJob::view).toList());
        m.put("note", "PayFast Aggregation bills the LegalSuite Light seat and public-network minutes. Client fee invoices and trust receipts are not collected here. The operator issues the VAT tax invoice. The R1,199 seat is not grossed up inside PayFast.");
        return m;
    }

    private Map<String, Object> payment(ProductPayment payment) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", payment.getId());
        m.put("kind", payment.getKind());
        m.put("mPaymentId", payment.getMerchantPaymentId());
        m.put("pfPaymentId", payment.getPfPaymentId());
        m.put("amountCents", payment.getAmountCents());
        m.put("amount", PayFastMoney.formatRands(payment.getAmountCents()));
        m.put("status", payment.getPaymentStatus());
        m.put("period", payment.getPeriod());
        m.put("createdAt", payment.getCreatedAt());
        return m;
    }
}
