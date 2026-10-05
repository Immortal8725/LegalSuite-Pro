package com.legalsuite.payfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalsuite.domain.BillingAccount;
import com.legalsuite.domain.MinutesInvoice;
import com.legalsuite.domain.ProductCheckout;
import com.legalsuite.domain.ProductPayment;
import com.legalsuite.repo.BillingAccountRepository;
import com.legalsuite.repo.MinutesInvoiceRepository;
import com.legalsuite.repo.ProductCheckoutRepository;
import com.legalsuite.repo.ProductPaymentRepository;
import com.legalsuite.service.AuditService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayFastItnServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SIGNATURE = "dbca3407feb0e9743dfd61dc6e1b5d29";

    @Mock BillingAccountRepository accounts;
    @Mock ProductPaymentRepository payments;
    @Mock ProductCheckoutRepository checkouts;
    @Mock MinutesInvoiceRepository minutes;
    @Mock PayFastServerConfirm confirm;
    @Mock AuditService audit;

    private PayFastItnService service;
    private ProductCheckout checkout;

    @BeforeEach
    void setUp() {
        PayFastProperties props = new PayFastProperties();
        props.setMerchantId("10000100");
        props.setMerchantKey("46f0cd694581a");
        props.setPassphrase("sandbox-passphrase");
        props.setEnv("sandbox");
        service = new PayFastItnService(props, accounts, payments, checkouts, minutes, confirm, audit);
        checkout = new ProductCheckout();
        checkout.setTenantId(TENANT);
        checkout.setMerchantPaymentId("seat-abc");
        checkout.setKind("seat");
        checkout.setSeatCount(1);
        checkout.setAmountCents(119_900L);
        checkout.setStatus("pending");
    }

    @Test
    void completeItnActivatesTheSeatAndStoresTheToken() {
        when(payments.findByPfPaymentId("1089250")).thenReturn(Optional.empty());
        when(confirm.confirmed(any())).thenReturn(true);
        when(checkouts.findByMerchantPaymentId("seat-abc")).thenReturn(Optional.of(checkout));
        when(accounts.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checkouts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayFastItnService.ItnOutcome outcome = service.handle(posted(SIGNATURE));

        assertEquals(200, outcome.httpStatus());
        assertEquals("complete", outcome.reason());
        assertEquals("complete", checkout.getStatus());
        ArgumentCaptor<BillingAccount> account = ArgumentCaptor.forClass(BillingAccount.class);
        verify(accounts).save(account.capture());
        assertEquals("active", account.getValue().getSeatStatus());
        assertEquals(1, account.getValue().getSeatCount());
        assertEquals(119_900L, account.getValue().getLastAmountCents());
        assertEquals("1089250", account.getValue().getLastPfPaymentId());
        assertEquals("seat-abc", account.getValue().getLastMerchantPaymentId());
        assertEquals("00000000-0000-0000-0000-00000000abcd", account.getValue().getPayfastToken());
        assertTrue(account.getValue().getSeatActiveUntil() != null);
        ArgumentCaptor<ProductPayment> payment = ArgumentCaptor.forClass(ProductPayment.class);
        verify(payments).save(payment.capture());
        assertEquals("1089250", payment.getValue().getPfPaymentId());
        assertEquals("seat-abc", payment.getValue().getMerchantPaymentId());
        assertEquals("00000000-0000-0000-0000-00000000abcd", payment.getValue().getToken());
        assertEquals(119_900L, payment.getValue().getAmountCents());
        assertEquals("complete", payment.getValue().getPaymentStatus());
        verify(confirm).confirmed(any());
    }

    @Test
    void badSignatureDoesNotConfirmOrPersist() {
        PayFastItnService.ItnOutcome outcome = service.handle(posted("00000000000000000000000000000000"));
        assertEquals(400, outcome.httpStatus());
        assertEquals("signature", outcome.reason());
        verify(confirm, never()).confirmed(any());
        verify(payments, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void serverConfirmFailureDoesNotActivate() {
        when(payments.findByPfPaymentId("1089250")).thenReturn(Optional.empty());
        when(confirm.confirmed(any())).thenReturn(false);
        PayFastItnService.ItnOutcome outcome = service.handle(posted(SIGNATURE));
        assertEquals(400, outcome.httpStatus());
        assertEquals("confirm", outcome.reason());
        verify(payments, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void duplicatePfPaymentIdDoesNotExtendTheSeatAgain() {
        when(payments.findByPfPaymentId("1089250")).thenReturn(Optional.of(new ProductPayment()));
        PayFastItnService.ItnOutcome outcome = service.handle(posted(SIGNATURE));
        assertEquals(200, outcome.httpStatus());
        assertEquals("duplicate", outcome.reason());
        verify(confirm, never()).confirmed(any());
        verify(accounts, never()).save(any());
        assertEquals("pending", checkout.getStatus());
    }

    @Test
    void amountMismatchIsStoredAndDoesNotActivate() {
        List<PayFastSignature.Field> fields = List.of(
                field("m_payment_id", "seat-abc"),
                field("pf_payment_id", "1089251"),
                field("payment_status", "COMPLETE"),
                field("amount_gross", "10.00"),
                field("custom_str1", TENANT.toString()),
                field("custom_str2", "seat"),
                field("custom_int1", "1"),
                field("token", "00000000-0000-0000-0000-00000000abcd"));
        String signature = PayFastSignature.sign(fields, "sandbox-passphrase");
        fields = new java.util.ArrayList<>(fields);
        fields.add(field("signature", signature));
        when(payments.findByPfPaymentId("1089251")).thenReturn(Optional.empty());
        when(confirm.confirmed(any())).thenReturn(true);
        when(checkouts.findByMerchantPaymentId("seat-abc")).thenReturn(Optional.of(checkout));
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayFastItnService.ItnOutcome outcome = service.handle(fields);

        assertEquals(200, outcome.httpStatus());
        assertEquals("amount_mismatch", outcome.reason());
        ArgumentCaptor<ProductPayment> payment = ArgumentCaptor.forClass(ProductPayment.class);
        verify(payments).save(payment.capture());
        assertEquals("rejected", payment.getValue().getPaymentStatus());
        assertNull(payment.getValue().getToken());
        verify(accounts, never()).save(any());
        assertEquals("pending", checkout.getStatus());
    }

    @Test
    void minutesItnStoresTheChargeWithoutActivatingASeat() {
        ProductCheckout minutesCheckout = new ProductCheckout();
        minutesCheckout.setTenantId(TENANT);
        minutesCheckout.setMerchantPaymentId("min-abc");
        minutesCheckout.setKind("minutes");
        minutesCheckout.setAmountCents(1_250L);
        minutesCheckout.setPeriod("2026-09");
        MinutesInvoice invoice = new MinutesInvoice();
        invoice.setMerchantPaymentId("min-abc");
        invoice.setStatus("issued");
        List<PayFastSignature.Field> fields = List.of(
                field("m_payment_id", "min-abc"),
                field("pf_payment_id", "555"),
                field("payment_status", "COMPLETE"),
                field("amount_gross", "12.50"),
                field("custom_str1", TENANT.toString()),
                field("custom_str2", "minutes"),
                field("token", "00000000-0000-0000-0000-00000000abcd"));
        fields = new java.util.ArrayList<>(fields);
        fields.add(field("signature", PayFastSignature.sign(fields, "sandbox-passphrase")));
        when(payments.findByPfPaymentId("555")).thenReturn(Optional.empty());
        when(confirm.confirmed(any())).thenReturn(true);
        when(checkouts.findByMerchantPaymentId("min-abc")).thenReturn(Optional.of(minutesCheckout));
        when(accounts.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(minutes.findByMerchantPaymentId("min-abc")).thenReturn(Optional.of(invoice));
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checkouts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(minutes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayFastItnService.ItnOutcome outcome = service.handle(fields);

        assertEquals(200, outcome.httpStatus());
        ArgumentCaptor<BillingAccount> account = ArgumentCaptor.forClass(BillingAccount.class);
        verify(accounts).save(account.capture());
        assertEquals("inactive", account.getValue().getSeatStatus());
        assertEquals("00000000-0000-0000-0000-00000000abcd", account.getValue().getPayfastToken());
        assertEquals("charged", invoice.getStatus());
        assertEquals("555", invoice.getPfPaymentId());
    }

    private static List<PayFastSignature.Field> posted(String signature) {
        return List.of(
                field("m_payment_id", "seat-abc"),
                field("pf_payment_id", "1089250"),
                field("payment_status", "COMPLETE"),
                field("item_name", "LegalSuite Light seat"),
                field("amount_gross", "1199.00"),
                field("amount_fee", "-27.58"),
                field("amount_net", "1171.42"),
                field("custom_str1", TENANT.toString()),
                field("custom_str2", "seat"),
                field("custom_int1", "1"),
                field("token", "00000000-0000-0000-0000-00000000abcd"),
                field("merchant_id", "10000100"),
                field("signature", signature));
    }

    private static PayFastSignature.Field field(String name, String value) {
        return new PayFastSignature.Field(name, value);
    }
}
