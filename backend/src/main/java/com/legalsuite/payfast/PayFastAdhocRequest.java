package com.legalsuite.payfast;

import java.util.ArrayList;
import java.util.List;

/** Header and body fields for {@code POST /subscriptions/{token}/adhoc}. The signature sorts every key, including the passphrase. */
public final class PayFastAdhocRequest {
    public record Prepared(String signature, String body, List<PayFastSignature.Field> signedFields) {}

    private PayFastAdhocRequest() {}

    public static Prepared prepare(
            String merchantId,
            String passphrase,
            String timestamp,
            String amountCents,
            String itemName,
            String itemDescription,
            String mPaymentId) {
        List<PayFastSignature.Field> fields = new ArrayList<>();
        fields.add(new PayFastSignature.Field("merchant-id", merchantId));
        fields.add(new PayFastSignature.Field("version", "v1"));
        fields.add(new PayFastSignature.Field("timestamp", timestamp));
        fields.add(new PayFastSignature.Field("amount", amountCents));
        fields.add(new PayFastSignature.Field("item_name", itemName));
        if (itemDescription != null && !itemDescription.isBlank()) {
            fields.add(new PayFastSignature.Field("item_description", itemDescription));
        }
        if (mPaymentId != null && !mPaymentId.isBlank()) {
            fields.add(new PayFastSignature.Field("m_payment_id", mPaymentId));
        }
        String signature = PayFastSignature.signAlphabetical(fields, passphrase);
        StringBuilder body = new StringBuilder();
        appendBody(body, "amount", amountCents);
        appendBody(body, "item_name", itemName);
        if (itemDescription != null && !itemDescription.isBlank()) appendBody(body, "item_description", itemDescription);
        if (mPaymentId != null && !mPaymentId.isBlank()) appendBody(body, "m_payment_id", mPaymentId);
        return new Prepared(signature, body.toString(), List.copyOf(fields));
    }

    private static void appendBody(StringBuilder body, String name, String value) {
        if (body.length() > 0) body.append('&');
        body.append(name).append('=').append(PayFastSignature.phpUrlEncode(value));
    }
}
