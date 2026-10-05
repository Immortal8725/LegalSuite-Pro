package com.legalsuite.voice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Validates {@code X-Twilio-Signature} (HMAC-SHA1 over the exact callback URL plus sorted POST fields). */
public final class TwilioSignature {
    private TwilioSignature() {}

    public static boolean valid(String authToken, String url, Map<String, String[]> params, String signature) {
        if (authToken == null || authToken.isBlank() || url == null || signature == null || signature.isBlank()) {
            return false;
        }
        StringBuilder data = new StringBuilder(url);
        if (params != null) {
            params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String[] values = entry.getValue() == null ? new String[] {""} : entry.getValue();
                        for (String value : values) {
                            data.append(entry.getKey()).append(value == null ? "" : value);
                        }
                    });
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
