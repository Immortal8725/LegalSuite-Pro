package com.legalsuite.payfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalsuite.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayFastSignatureTest {
    private static final String PASSPHRASE = "sandbox-passphrase";

    @Test
    void checkoutSignatureMatchesTheFixture() {
        List<PayFastSignature.Field> fields = List.of(
                field("merchant_id", "10000100"),
                field("merchant_key", "46f0cd694581a"),
                field("return_url", "https://app.example.com/product-billing/return"),
                field("cancel_url", "https://app.example.com/product-billing/cancel"),
                field("notify_url", "https://app.example.com/api/v1/product-billing/payfast/itn"),
                field("name_first", "Thabo"),
                field("name_last", "Ndlovu"),
                field("email_address", "thabo@ndlovulaw.co.za"),
                field("m_payment_id", "seat-abc"),
                field("amount", "1199.00"),
                field("item_name", "LegalSuite Light seat"),
                field("item_description", "1 attorney seat"),
                field("custom_int1", "1"),
                field("custom_str1", "11111111-1111-1111-1111-111111111111"),
                field("custom_str2", "seat"),
                field("subscription_type", "1"),
                field("billing_date", "2026-10-05"),
                field("recurring_amount", "1199.00"),
                field("frequency", "3"),
                field("cycles", "0"));
        String canonical = "merchant_id=10000100&merchant_key=46f0cd694581a"
                + "&return_url=https%3A%2F%2Fapp.example.com%2Fproduct-billing%2Freturn"
                + "&cancel_url=https%3A%2F%2Fapp.example.com%2Fproduct-billing%2Fcancel"
                + "&notify_url=https%3A%2F%2Fapp.example.com%2Fapi%2Fv1%2Fproduct-billing%2Fpayfast%2Fitn"
                + "&name_first=Thabo&name_last=Ndlovu&email_address=thabo%40ndlovulaw.co.za"
                + "&m_payment_id=seat-abc&amount=1199.00&item_name=LegalSuite+Light+seat"
                + "&item_description=1+attorney+seat&custom_int1=1"
                + "&custom_str1=11111111-1111-1111-1111-111111111111&custom_str2=seat"
                + "&subscription_type=1&billing_date=2026-10-05&recurring_amount=1199.00"
                + "&frequency=3&cycles=0&passphrase=sandbox-passphrase";
        assertEquals(canonical, PayFastSignature.canonical(fields, PASSPHRASE));
        assertEquals("4f8a7cac2b0c50c7bf3e8584c750cc04", PayFastSignature.sign(fields, PASSPHRASE));
        assertTrue(PayFastSignature.matches(fields, PASSPHRASE, "4f8a7cac2b0c50c7bf3e8584c750cc04"));
        assertFalse(PayFastSignature.matches(fields, PASSPHRASE, "00000000000000000000000000000000"));
    }

    @Test
    void itnSignatureMatchesTheFixtureAndRejectsATamperedAmount() {
        List<PayFastSignature.Field> fields = itn("1199.00");
        String canonical = "m_payment_id=seat-abc&pf_payment_id=1089250&payment_status=COMPLETE"
                + "&item_name=LegalSuite+Light+seat&amount_gross=1199.00&amount_fee=-27.58"
                + "&amount_net=1171.42&custom_str1=11111111-1111-1111-1111-111111111111"
                + "&custom_str2=seat&custom_int1=1&token=00000000-0000-0000-0000-00000000abcd"
                + "&merchant_id=10000100&passphrase=sandbox-passphrase";
        assertEquals(canonical, PayFastSignature.canonical(fields, PASSPHRASE));
        assertEquals("dbca3407feb0e9743dfd61dc6e1b5d29", PayFastSignature.sign(fields, PASSPHRASE));
        assertFalse(PayFastSignature.matches(itn("10.00"), PASSPHRASE, "dbca3407feb0e9743dfd61dc6e1b5d29"));
        assertFalse(PayFastSignature.matches(fields, "other-passphrase", "dbca3407feb0e9743dfd61dc6e1b5d29"));
        assertFalse(PayFastSignature.matches(fields, PASSPHRASE, ""));
    }

    @Test
    void adhocSignatureIsAlphabetical() {
        PayFastAdhocRequest.Prepared prepared = PayFastAdhocRequest.prepare(
                "10000100",
                PASSPHRASE,
                "2026-10-05T18:00:00+00:00",
                "119900",
                "LegalSuite phone minutes",
                "2026-09 public-network minutes",
                "min-abc");
        String canonical = "amount=119900&item_description=2026-09+public-network+minutes"
                + "&item_name=LegalSuite+phone+minutes&m_payment_id=min-abc&merchant-id=10000100"
                + "&timestamp=2026-10-05T18%3A00%3A00%2B00%3A00&version=v1&passphrase=sandbox-passphrase";
        assertEquals(canonical, PayFastSignature.canonical(sortedCopy(prepared.signedFields()), PASSPHRASE));
        assertEquals("243a87b8804ac4c26e33de7e47928128", prepared.signature());
    }

    @Test
    void encodesStarSkipsBlanksAndOmitsAnEmptyPassphrase() {
        List<PayFastSignature.Field> star = List.of(field("item_name", "Light * seat"), field("amount", "1199.00"));
        assertEquals(
                "item_name=Light+%2A+seat&amount=1199.00&passphrase=sandbox-passphrase",
                PayFastSignature.canonical(star, PASSPHRASE));
        assertEquals("1d5ba885bef451f5e747d916aec65159", PayFastSignature.sign(star, PASSPHRASE));

        List<PayFastSignature.Field> blanks = List.of(
                field("merchant_id", "10000100"),
                field("cell_number", ""),
                field("amount", "1199.00"));
        assertEquals("merchant_id=10000100&amount=1199.00&passphrase=pw", PayFastSignature.canonical(blanks, "pw"));
        assertEquals("43c56d5787841929c8dc2fb86bd5adc3", PayFastSignature.sign(blanks, "pw"));

        List<PayFastSignature.Field> pair = List.of(field("merchant_id", "10000100"), field("merchant_key", "46f0cd694581a"));
        assertEquals("af91b63243bdf01bcbbf04e6b7c713c2", PayFastSignature.sign(pair, null));
        assertEquals("af91b63243bdf01bcbbf04e6b7c713c2", PayFastSignature.sign(pair, "   "));
    }

    @Test
    void seatAmountIs119900CentsPerSeat() {
        assertEquals(119_900L, PayFastMoney.LIGHT_SEAT_CENTS);
        assertEquals(119_900L, PayFastMoney.seatsCents(1));
        assertEquals(239_800L, PayFastMoney.seatsCents(2));
        assertEquals("1199.00", PayFastMoney.formatRands(119_900));
        assertEquals("2398.00", PayFastMoney.formatRands(239_800));
        assertEquals("12.50", PayFastMoney.formatRands(1_250));
        assertEquals("0.00", PayFastMoney.formatRands(0));
        assertEquals(-2_758L, PayFastMoney.parseRands("-27.58"));
        assertEquals(119_900L, PayFastMoney.parseRands("1199.00"));
        assertThrows(ApiException.class, () -> PayFastMoney.seatsCents(0));
    }

    @Test
    void adhocResponseParserDoesNotOpenASocket() {
        PayFastAdhocGateway.AdhocReceipt ok = PayFastHttpAdhoc.read(
                200,
                "{\"code\":200,\"status\":\"success\",\"data\":{\"response\":\"true\",\"pf_payment_id\":\"999\",\"message\":\"ok\"}}");
        assertTrue(ok.accepted());
        assertEquals("999", ok.pfPaymentId());
        assertFalse(PayFastHttpAdhoc.read(400, "{\"status\":\"failed\",\"data\":{\"message\":\"no\"}}").accepted());
        assertFalse(PayFastHttpAdhoc.read(200, "not json").accepted());
    }

    private static List<PayFastSignature.Field> itn(String gross) {
        return List.of(
                field("m_payment_id", "seat-abc"),
                field("pf_payment_id", "1089250"),
                field("payment_status", "COMPLETE"),
                field("item_name", "LegalSuite Light seat"),
                field("amount_gross", gross),
                field("amount_fee", "-27.58"),
                field("amount_net", "1171.42"),
                field("custom_str1", "11111111-1111-1111-1111-111111111111"),
                field("custom_str2", "seat"),
                field("custom_int1", "1"),
                field("token", "00000000-0000-0000-0000-00000000abcd"),
                field("merchant_id", "10000100"));
    }

    private static PayFastSignature.Field field(String name, String value) {
        return new PayFastSignature.Field(name, value);
    }

    private static List<PayFastSignature.Field> sortedCopy(List<PayFastSignature.Field> fields) {
        return fields.stream()
                .sorted(java.util.Comparator.comparing(PayFastSignature.Field::name))
                .toList();
    }
}
