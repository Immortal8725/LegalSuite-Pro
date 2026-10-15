package com.legalsuite.voice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Twilio Voice SDK access token: an HS256 JWT signed with an API key secret.
 * Format: https://www.twilio.com/docs/iam/access-tokens
 */
public final class TwilioAccessToken {
    private TwilioAccessToken() {}

    public static String voice(
            String accountSid,
            String apiKeySid,
            String apiKeySecret,
            String identity,
            String twimlAppSid,
            String pushCredentialSid,
            long ttlSeconds,
            Instant now) {
        Map<String, Object> outgoing = new LinkedHashMap<>();
        outgoing.put("application_sid", twimlAppSid);
        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("incoming", Map.of("allow", true));
        voice.put("outgoing", outgoing);
        if (pushCredentialSid != null && !pushCredentialSid.isBlank()) voice.put("push_credential_sid", pushCredentialSid);
        Map<String, Object> grants = new LinkedHashMap<>();
        grants.put("identity", identity);
        grants.put("voice", voice);
        // HS256 needs a key of at least 256 bits in jjwt. Twilio secrets are 32 characters, which is enough.
        byte[] secret = apiKeySecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("TWILIO_API_KEY_SECRET is too short to sign a token.");
        }
        return Jwts.builder()
                .header().add("cty", "twilio-fpa;v=1").add("typ", "JWT").and()
                .id(apiKeySid + "-" + now.getEpochSecond())
                .issuer(apiKeySid)
                .subject(accountSid)
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claim("grants", grants)
                .signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS256)
                .compact();
    }
}
