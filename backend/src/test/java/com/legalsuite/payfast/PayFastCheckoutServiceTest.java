package com.legalsuite.payfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayFastCheckoutServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock BillingAccountRepository accounts;
    @Mock ProductCheckoutRepository checkouts;
    @Mock AppUserRepository users;
    @Mock AuditService audit;

    private PayFastProperties props;
    private PayFastCheckoutService service;

    @BeforeEach
    void setUp() {
        props = new PayFastProperties();
        props.setMerchantId("10000100");
        props.setMerchantKey("46f0cd694581a");
        props.setPassphrase("sandbox-passphrase");
        props.setEnv("sandbox");
        props.setReturnUrl("https://app.example.com/product-billing/return");
        props.setCancelUrl("https://app.example.com/product-billing/cancel");
        props.setNotifyUrl("https://app.example.com/api/v1/product-billing/payfast/itn");
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T06:00:00Z"), ZoneId.of("Africa/Johannesburg"));
        service = new PayFastCheckoutService(props, accounts, checkouts, users, audit, clock);
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        TenantContext.setRole("owner");
        TenantContext.setEmail("thabo@ndlovulaw.co.za");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerCheckoutPersistsAPendingSeatAndASignedForm() {
        AppUser user = new AppUser();
        user.setFirstName("Thabo");
        user.setLastName("Ndlovu");
        user.setEmail("thabo@ndlovulaw.co.za");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checkouts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = service.seatCheckout(1);

        assertEquals("https://sandbox.payfast.co.za/eng/process", body.get("action"));
        assertEquals("recurring", body.get("subscriptionType"));
        assertEquals("monthly", body.get("frequency"));
        assertEquals(119_900L, body.get("amountCents"));
        assertEquals("1199.00", body.get("amount"));
        @SuppressWarnings("unchecked")
        var fields = (java.util.List<Map<String, String>>) body.get("fields");
        assertTrueSignature(fields);
        ArgumentCaptor<BillingAccount> account = ArgumentCaptor.forClass(BillingAccount.class);
        verify(accounts).save(account.capture());
        assertEquals("pending", account.getValue().getSeatStatus());
        assertEquals(1, account.getValue().getSeatCount());
        ArgumentCaptor<ProductCheckout> row = ArgumentCaptor.forClass(ProductCheckout.class);
        verify(checkouts).save(row.capture());
        assertEquals("seat", row.getValue().getKind());
        assertEquals(119_900L, row.getValue().getAmountCents());
    }

    @Test
    void associateCannotStartCheckout() {
        TenantContext.setRole("associate");
        ApiException ex = assertThrows(ApiException.class, () -> service.seatCheckout(1));
        assertEquals(403, ex.getStatus().value());
        verify(checkouts, never()).save(any());
    }

    @Test
    void missingMerchantSettingsRefuseCheckout() {
        props.setMerchantId("");
        ApiException ex = assertThrows(ApiException.class, () -> service.seatCheckout(1));
        assertEquals(400, ex.getStatus().value());
        verify(checkouts, never()).save(any());
    }

    private static void assertTrueSignature(java.util.List<Map<String, String>> fields) {
        java.util.List<PayFastSignature.Field> signed = fields.stream()
                .map(field -> new PayFastSignature.Field(field.get("name"), field.get("value")))
                .toList();
        String signature = PayFastSignature.value(signed, "signature");
        org.junit.jupiter.api.Assertions.assertTrue(PayFastSignature.matches(signed, "sandbox-passphrase", signature));
        org.junit.jupiter.api.Assertions.assertTrue(signed.stream().noneMatch(field -> "passphrase".equals(field.name())));
    }
}
