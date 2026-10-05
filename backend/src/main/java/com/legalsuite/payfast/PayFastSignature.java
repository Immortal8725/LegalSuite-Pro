package com.legalsuite.payfast;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * PayFast MD5 signatures.
 * Checkout and ITN use the posted field order, then append the passphrase.
 * The adhoc API sorts every key, including the passphrase, and does not append it again.
 * Encoding matches PHP {@code urlencode}: spaces are {@code +}, {@code *} is {@code %2A}.
 */
public final class PayFastSignature {
    public record Field(String name, String value) {}

    private PayFastSignature() {}

    public static String phpUrlEncode(String value) {
        String trimmed = value == null ? "" : value.trim();
        return URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("*", "%2A");
    }

    public static String canonical(List<Field> fields, String passphrase) {
        StringBuilder sb = new StringBuilder();
        if (fields != null) {
            for (Field field : fields) {
                append(sb, field);
            }
        }
        appendPassphrase(sb, passphrase);
        return sb.toString();
    }

    public static String sign(List<Field> fields, String passphrase) {
        return md5(canonical(fields, passphrase));
    }

    /**
     * PayFast API signature. The passphrase is one of the sorted variables, not a suffix.
     * Checkout and ITN must keep using {@link #sign}, which appends the passphrase.
     */
    public static String signAlphabetical(List<Field> fields, String passphrase) {
        return md5(canonicalAlphabetical(fields, passphrase));
    }

    /** Sorted API string, including the passphrase in key order. No trailing passphrase suffix. */
    public static String canonicalAlphabetical(List<Field> fields, String passphrase) {
        List<Field> sorted = new ArrayList<>();
        if (fields != null) {
            for (Field field : fields) {
                if (field == null || field.name() == null || "signature".equals(field.name())) continue;
                if ("passphrase".equals(field.name())) continue;
                if (field.value() == null || field.value().isEmpty()) continue;
                sorted.add(field);
            }
        }
        if (passphrase != null && !passphrase.isBlank()) {
            sorted.add(new Field("passphrase", passphrase.trim()));
        }
        sorted.sort(Comparator.comparing(Field::name));
        return canonical(sorted, null);
    }

    public static boolean matches(List<Field> fields, String passphrase, String postedSignature) {
        if (postedSignature == null || postedSignature.isBlank()) return false;
        String expected = sign(fields, passphrase);
        String actual = postedSignature.trim().toLowerCase(Locale.ROOT);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** Form body for the ITN server confirm. Includes the signature field. Skips blanks. */
    public static String formBody(List<Field> fields) {
        StringBuilder sb = new StringBuilder();
        if (fields == null) return "";
        for (Field field : fields) {
            if (field == null || field.name() == null || field.name().isBlank()) continue;
            if (field.value() == null || field.value().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(field.name()).append('=').append(phpUrlEncode(field.value()));
        }
        return sb.toString();
    }

    public static String value(List<Field> fields, String name) {
        if (fields == null || name == null) return "";
        for (Field field : fields) {
            if (field != null && name.equals(field.name()) && field.value() != null) {
                return field.value();
            }
        }
        return "";
    }

    private static void append(StringBuilder sb, Field field) {
        if (field == null || field.name() == null || "signature".equals(field.name())) return;
        if (field.value() == null || field.value().isEmpty()) return;
        if (sb.length() > 0) sb.append('&');
        sb.append(field.name()).append('=').append(phpUrlEncode(field.value()));
    }

    private static void appendPassphrase(StringBuilder sb, String passphrase) {
        if (passphrase == null || passphrase.isBlank()) return;
        if (sb.length() > 0) sb.append('&');
        sb.append("passphrase=").append(phpUrlEncode(passphrase));
    }

    private static String md5(String payload) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is not available", ex);
        }
    }
}
