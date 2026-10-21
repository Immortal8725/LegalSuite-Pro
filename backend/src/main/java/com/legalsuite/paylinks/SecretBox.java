package com.legalsuite.paylinks;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM for small secrets at rest (a firm's PayFast key and passphrase). Key: SECRETS_ENCRYPTION_KEY. */
@Component
public class SecretBox {
    private final byte[] key;

    public SecretBox(@Value("${legalsuite.secrets.encryption-key:}") String b64) {
        byte[] k = null;
        try {
            if (b64 != null && !b64.isBlank()) k = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException ignored) {
            k = null;
        }
        this.key = k != null && k.length == 32 ? k : null;
    }

    public boolean ready() { return key != null; }

    public String seal(String plain) {
        if (plain == null) return null;
        if (key == null) throw new IllegalStateException("SECRETS_ENCRYPTION_KEY is not set");
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[12 + ct.length];
            System.arraycopy(iv, 0, all, 0, 12);
            System.arraycopy(ct, 0, all, 12, ct.length);
            return "v1:" + Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("seal failed", e);
        }
    }

    public String open(String sealed) {
        if (sealed == null || key == null || !sealed.startsWith("v1:")) return null;
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
