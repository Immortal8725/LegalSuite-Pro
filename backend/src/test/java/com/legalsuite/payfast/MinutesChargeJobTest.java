package com.legalsuite.payfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.MinutesInvoice;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.ProductCheckoutRepository;
import com.legalsuite.repo.ProductPaymentRepository;
import com.legalsuite.service.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class MinutesChargeJobTest {
    private final UUID tenantId = UUID.randomUUID();

    @Mock CallRecordRepository calls;
    @Mock MinutesInvoiceRepository invoices;
    @Mock BillingAccountRepository accounts;
    @Mock ProductCheckoutRepository checkouts;
    @Mock ProductPaymentRepository payments;
    @Mock PayFastAdhocGateway gateway;
    @Mock AuditService audit;

    private PayFastProperties props;
    private MinutesChargeJob job;

    @BeforeEach
    void setUp() {
        props = new PayFastProperties();
        props.setMerchantId("10000100");
        props.setMerchantKey("46f0cd694581a");
        props.setPassphrase("sandbox-passphrase");
        props.setEnv("sandbox");
        job = new MinutesChargeJob(
                calls, invoices, accounts, checkouts, payments, gateway, props, audit,
                Clock.systemUTC());
        TenantContext.setTenantId(tenantId);
        TenantContext.setRole("owner");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chargesTokenisedMinutesFromTheUsageTotal() {
        when(invoices.findByTenantIdAndPeriod(tenantId, "2026-09")).thenReturn(Optional.empty());
        when(calls.findByTenantIdOrderByStartedAtDesc(tenantId)).thenReturn(List.of(
                call("pstn_outbound", "2026-09-15T10:00:00Z", 120, "12.50"),
                call("webrtc", "2026-09-15T11:00:00Z", 600, "99.00"),
                call("pstn_inbound", "2026-08-31T23:00:00Z", 60, "4.00")));
        BillingAccount account = new BillingAccount();
        account.setTenantId(tenantId);
        account.setPayfastToken("00000000-0000-0000-0000-00000000abcd");
        account.setSeatStatus("active");
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.of(account));
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checkouts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findByPfPaymentId("777")).thenReturn(Optional.empty());
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.charge(any())).thenReturn(PayFastAdhocGateway.AdhocReceipt.ok("777", "accepted"));

        Map<String, Object> body = job.charge("2026-09");

        assertEquals("charged", body.get("status"));
        assertEquals(2, body.get("minutes"));
        assertEquals(1_250L, body.get("amountCents"));
        assertEquals("12.50", body.get("amount"));
        assertEquals("777", body.get("pfPaymentId"));
        ArgumentCaptor<PayFastAdhocGateway.AdhocCharge> charge = ArgumentCaptor.forClass(PayFastAdhocGateway.AdhocCharge.class);
        verify(gateway).charge(charge.capture());
        assertEquals(1_250L, charge.getValue().amountCents());
        assertEquals("00000000-0000-0000-0000-00000000abcd", charge.getValue().token());
        assertTrue(String.valueOf(charge.getValue().mPaymentId()).startsWith("min-"));
    }

    @Test
    void missingTokenIssuesTheInvoiceAndDoesNotCallPayFast() {
        when(invoices.findByTenantIdAndPeriod(tenantId, "2026-09")).thenReturn(Optional.empty());
        when(calls.findByTenantIdOrderByStartedAtDesc(tenantId)).thenReturn(List.of(
                call("pstn_outbound", "2026-09-02T00:00:00Z", 60, "3.00")));
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = job.charge("2026-09");

        assertEquals("issued", body.get("status"));
        assertEquals(300L, body.get("amountCents"));
        verify(gateway, never()).charge(any());
    }

    @Test
    void zeroCostMinutesAreNotSentToPayFast() {
        when(invoices.findByTenantIdAndPeriod(tenantId, "2026-09")).thenReturn(Optional.empty());
        when(calls.findByTenantIdOrderByStartedAtDesc(tenantId)).thenReturn(List.of(
                call("pstn_outbound", "2026-09-02T00:00:00Z", 180, "0")));
        when(invoices.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = job.charge("2026-09");

        assertEquals("nothing_due", body.get("status"));
        assertEquals(3, body.get("minutes"));
        assertEquals(0L, body.get("amountCents"));
        verify(gateway, never()).charge(any());
        verify(accounts, never()).findByTenantId(any());
    }

    @Test
    void anAlreadyChargedPeriodIsNotChargedAgain() {
        MinutesInvoice invoice = new MinutesInvoice();
        invoice.setPeriod("2026-09");
        invoice.setStatus("charged");
        invoice.setAmountCents(1_250L);
        invoice.setPfPaymentId("777");
        when(invoices.findByTenantIdAndPeriod(tenantId, "2026-09")).thenReturn(Optional.of(invoice));

        Map<String, Object> body = job.charge("2026-09");

        assertEquals("charged", body.get("status"));
        verify(gateway, never()).charge(any());
        verify(calls, never()).findByTenantIdOrderByStartedAtDesc(any());
    }

    private static void assertTrue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }

    private CallRecord call(String type, String started, int seconds, String cost) {
        CallRecord call = new CallRecord();
        call.setTenantId(tenantId);
        call.setCallType(type);
        call.setStartedAt(Instant.parse(started).atOffset(ZoneOffset.UTC).toInstant());
        call.setDurationSeconds(seconds);
        call.setTotalCost(new BigDecimal(cost));
        return call;
    }
}
