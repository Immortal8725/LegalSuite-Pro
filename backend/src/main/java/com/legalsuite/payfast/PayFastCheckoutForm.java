package com.legalsuite.payfast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Signed PayFast checkout fields.
 * Recurring seats use PayFast {@code subscription_type=1} and {@code frequency=3} (monthly).
 * Card tokenisation uses {@code subscription_type=2} so later minutes can be charged adhoc.
 */
public final class PayFastCheckoutForm {
    public static final String SUBSCRIPTION_RECURRING = "1";
    public static final String SUBSCRIPTION_TOKEN = "2";
    public static final String FREQUENCY_MONTHLY = "3";

    public record Built(
            String action,
            String environment,
            long amountCents,
            String amount,
            String subscriptionType,
            String frequency,
            String mPaymentId,
            List<PayFastSignature.Field> fields) {}

    private PayFastCheckoutForm() {}

    public static Built seat(
            PayFastProperties props,
            UUID tenantId,
            String firstName,
            String lastName,
            String email,
            String mPaymentId,
            int seats,
            LocalDate billingDate) {
        long cents = PayFastMoney.seatsCents(seats);
        String amount = PayFastMoney.formatRands(cents);
        String description = seats == 1 ? "1 attorney seat" : seats + " attorney seats";
        List<PayFastSignature.Field> fields = base(
                props, firstName, lastName, email, mPaymentId, amount,
                "LegalSuite Light seat", description, String.valueOf(seats), tenantId, "seat");
        fields.add(new PayFastSignature.Field("subscription_type", SUBSCRIPTION_RECURRING));
        fields.add(new PayFastSignature.Field("billing_date", billingDate.toString()));
        fields.add(new PayFastSignature.Field("recurring_amount", amount));
        fields.add(new PayFastSignature.Field("frequency", FREQUENCY_MONTHLY));
        fields.add(new PayFastSignature.Field("cycles", "0"));
        return finish(props, "recurring", "monthly", mPaymentId, cents, amount, fields);
    }

    public static Built token(
            PayFastProperties props,
            UUID tenantId,
            String firstName,
            String lastName,
            String email,
            String mPaymentId) {
        List<PayFastSignature.Field> fields = base(
                props, firstName, lastName, email, mPaymentId, "0.00",
                "LegalSuite card token", "Card on file for phone minutes", "0", tenantId, "token");
        fields.add(new PayFastSignature.Field("subscription_type", SUBSCRIPTION_TOKEN));
        return finish(props, "token", null, mPaymentId, 0L, "0.00", fields);
    }

    private static List<PayFastSignature.Field> base(
            PayFastProperties props,
            String firstName,
            String lastName,
            String email,
            String mPaymentId,
            String amount,
            String itemName,
            String itemDescription,
            String customInt1,
            UUID tenantId,
            String kind) {
        List<PayFastSignature.Field> fields = new ArrayList<>();
        fields.add(new PayFastSignature.Field("merchant_id", props.getMerchantId()));
        fields.add(new PayFastSignature.Field("merchant_key", props.getMerchantKey()));
        fields.add(new PayFastSignature.Field("return_url", props.getReturnUrl()));
        fields.add(new PayFastSignature.Field("cancel_url", props.getCancelUrl()));
        fields.add(new PayFastSignature.Field("notify_url", props.getNotifyUrl()));
        fields.add(new PayFastSignature.Field("name_first", blankToEmpty(firstName)));
        fields.add(new PayFastSignature.Field("name_last", blankToEmpty(lastName)));
        fields.add(new PayFastSignature.Field("email_address", blankToEmpty(email)));
        fields.add(new PayFastSignature.Field("m_payment_id", mPaymentId));
        fields.add(new PayFastSignature.Field("amount", amount));
        fields.add(new PayFastSignature.Field("item_name", itemName));
        fields.add(new PayFastSignature.Field("item_description", itemDescription));
        fields.add(new PayFastSignature.Field("custom_int1", customInt1));
        fields.add(new PayFastSignature.Field("custom_str1", tenantId.toString()));
        fields.add(new PayFastSignature.Field("custom_str2", kind));
        return fields;
    }

    private static Built finish(
            PayFastProperties props,
            String subscriptionType,
            String frequency,
            String mPaymentId,
            long cents,
            String amount,
            List<PayFastSignature.Field> fields) {
        String signature = PayFastSignature.sign(fields, props.getPassphrase());
        fields.add(new PayFastSignature.Field("signature", signature));
        return new Built(
                props.processUrl(),
                props.environment(),
                cents,
                amount,
                subscriptionType,
                frequency,
                mPaymentId,
                List.copyOf(fields));
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
