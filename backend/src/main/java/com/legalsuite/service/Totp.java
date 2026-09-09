package com.legalsuite.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (6 digits, 30s, HMAC-SHA1). No vendor library. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RNG = new SecureRandom();

    private Totp() {}

    public static String newSecret() {
        byte[] raw = new byte[20];
        RNG.nextBytes(raw);
        return base32(raw);
    }

    public static String otpauthUrl(String email, String secret) {
        String acct = email == null ? "user" : email.replace(" ", "%20");
        return "otpauth://totp/LegalSuitePro:" + acct + "?secret=" + secret
                + "&issuer=LegalSuitePro&digits=6&period=30";
    }

    public static boolean verify(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null) return false;
        String digits = code.replaceAll("\\s+", "");
        if (!digits.matches("\\d{6}")) return false;
        long step = Instant.now().getEpochSecond() / 30;
        for (int i = -1; i <= 1; i++) {
            if (codeAt(secret, step + i).equals(digits)) return true;
        }
        return false;
    }

    static String codeAt(String secret, long counter) {
        try {
            byte[] key = fromBase32(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP failed", e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] fromBase32(String secret) {
        String s = secret.toUpperCase().replace("=", "").replaceAll("\\s+", "");
        int buffer = 0;
        int bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            int val = ALPHABET.indexOf(s.charAt(i));
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
