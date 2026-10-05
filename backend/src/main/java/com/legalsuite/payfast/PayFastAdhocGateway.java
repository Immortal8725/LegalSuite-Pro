package com.legalsuite.payfast;

/** Charges a stored PayFast token. The HTTP implementation is not used in unit tests. */
public interface PayFastAdhocGateway {
    AdhocReceipt charge(AdhocCharge charge);

    record AdhocCharge(
            String token,
            long amountCents,
            String itemName,
            String itemDescription,
            String mPaymentId) {}

    record AdhocReceipt(boolean accepted, String pfPaymentId, String message) {
        public static AdhocReceipt ok(String pfPaymentId, String message) {
            return new AdhocReceipt(true, pfPaymentId == null ? "" : pfPaymentId, message == null ? "" : message);
        }

        public static AdhocReceipt failed(String message) {
            return new AdhocReceipt(false, "", message == null ? "PayFast did not accept the charge" : message);
        }
    }
}
