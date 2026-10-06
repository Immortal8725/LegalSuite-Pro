package com.legalsuite.payfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayFastCheckoutFormTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void seatFormUsesRecurringMonthlyAndTheFixtureSignature() {
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.seat(
                props("sandbox"),
                TENANT,
                "Thabo",
                "Ndlovu",
                "thabo@ndlovulaw.co.za",
                "seat-abc",
                1,
                LocalDate.parse("2026-10-05"));
        assertEquals("https://sandbox.payfast.co.za/eng/process", built.action());
        assertEquals("sandbox", built.environment());
        assertEquals(119_900L, built.amountCents());
        assertEquals("1199.00", built.amount());
        assertEquals("recurring", built.subscriptionType());
        assertEquals("monthly", built.frequency());
        assertEquals("1", value(built, "subscription_type"));
        assertEquals("3", value(built, "frequency"));
        assertEquals("0", value(built, "cycles"));
        assertEquals("1199.00", value(built, "recurring_amount"));
        assertEquals("seat", value(built, "custom_str2"));
        assertEquals("4f8a7cac2b0c50c7bf3e8584c750cc04", value(built, "signature"));
        assertTrue(built.fields().stream().noneMatch(field -> "passphrase".equals(field.name())));
        assertTrue(PayFastSignature.matches(built.fields(), "sandbox-passphrase", value(built, "signature")));
    }

    @Test
    void twoSeatsDoubleTheCentsAndTheRandAmount() {
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.seat(
                props("sandbox"), TENANT, "Thabo", "Ndlovu", "thabo@ndlovulaw.co.za", "seat-two", 2, LocalDate.parse("2026-10-05"));
        assertEquals(239_800L, built.amountCents());
        assertEquals("2398.00", built.amount());
        assertEquals("2", value(built, "custom_int1"));
        assertEquals("2 attorney seats", value(built, "item_description"));
    }

    @Test
    void tokenFormIsSubscriptionType2AndDoesNotScheduleASeat() {
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.token(
                props("sandbox"), TENANT, "Thabo", "Ndlovu", "thabo@ndlovulaw.co.za", "token-abc");
        assertEquals("token", built.subscriptionType());
        assertNull(built.frequency());
        assertEquals("2", value(built, "subscription_type"));
        assertEquals("0.00", value(built, "amount"));
        assertEquals("token", value(built, "custom_str2"));
        assertFalse(built.fields().stream().anyMatch(field -> "frequency".equals(field.name())));
        assertFalse(built.fields().stream().anyMatch(field -> "passphrase".equals(field.name())));
        assertTrue(PayFastSignature.matches(built.fields(), "sandbox-passphrase", value(built, "signature")));
    }

    @Test
    void liveEnvironmentPostsToTheLiveProcessHost() {
        PayFastCheckoutForm.Built built = PayFastCheckoutForm.seat(
                props("live"), TENANT, "Thabo", "Ndlovu", "thabo@ndlovulaw.co.za", "seat-abc", 1, LocalDate.parse("2026-10-05"));
        assertEquals("https://www.payfast.co.za/eng/process", built.action());
        assertEquals("live", built.environment());
        assertEquals("https://www.payfast.co.za/eng/query/validate", props("live").validateUrl());
        assertEquals(
                "https://api.payfast.co.za/subscriptions/00000000-0000-0000-0000-00000000abcd/adhoc",
                props("live").adhocUrl("00000000-0000-0000-0000-00000000abcd"));
        assertTrue(props("sandbox").adhocUrl("00000000-0000-0000-0000-00000000abcd").endsWith("?testing=true"));
    }

    private static String value(PayFastCheckoutForm.Built built, String name) {
        return PayFastSignature.value(built.fields(), name);
    }

    private static PayFastProperties props(String env) {
        PayFastProperties props = new PayFastProperties();
        props.setMerchantId("10000100");
        props.setMerchantKey("46f0cd694581a");
        props.setPassphrase("sandbox-passphrase");
        props.setEnv(env);
        props.setReturnUrl("https://app.example.com/product-billing/return");
        props.setCancelUrl("https://app.example.com/product-billing/cancel");
        props.setNotifyUrl("https://app.example.com/api/v1/product-billing/payfast/itn");
        return props;
    }
}
