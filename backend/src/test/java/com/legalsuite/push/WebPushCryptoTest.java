package com.legalsuite.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Encrypts like Owlaw, then decrypts like a browser would (RFC 8291). */
class WebPushCryptoTest {

    @Test
    void browserCanDecryptWhatOwlawSends() throws Exception {
        KeyPair ua = WebPushCrypto.newKeyPair();
        byte[] uaPub = WebPushCrypto.encode((ECPublicKey) ua.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] msg = "{\"title\":\"Deadline in 3 days\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(msg, uaPub, auth);

        ByteBuffer b = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        b.get(salt);
        assertEquals(4096, b.getInt());
        int idlen = b.get();
        assertEquals(65, idlen);
        byte[] asPub = new byte[65];
        b.get(asPub);
        byte[] cipher = new byte[b.remaining()];
        b.get(cipher);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushCrypto.publicKey(asPub), true);
        byte[] ecdh = ka.generateSecret();
        byte[] prkKey = WebPushCrypto.hmac(auth, ecdh);
        byte[] ikm = WebPushCrypto.hmac(prkKey, WebPushCrypto.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPub, asPub, new byte[] {1}));
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] cek = WebPushCrypto.first(WebPushCrypto.hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 16);
        byte[] nonce = WebPushCrypto.first(WebPushCrypto.hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = c.doFinal(cipher);
        assertEquals(2, plain[plain.length - 1]);
        assertArrayEquals(msg, Arrays.copyOf(plain, plain.length - 1));
    }

    @Test
    void vapidJwtVerifiesWithThePublicKey() throws Exception {
        KeyPair kp = WebPushCrypto.newKeyPair();
        byte[] pub = WebPushCrypto.encode((ECPublicKey) kp.getPublic());
        byte[] d = ((ECPrivateKey) kp.getPrivate()).getS().toByteArray();
        ECPrivateKey key = WebPushCrypto.privateKey(d);
        String h = WebPushCrypto.vapidHeader("https://fcm.googleapis.com/fcm/send/abc", "mailto:support@owlaw.co.za", pub, key, 1_800_000_000L);
        assertTrue(h.startsWith("vapid t="));
        String jwt = h.substring(8, h.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(WebPushCrypto.b64(parts[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"aud\":\"https://fcm.googleapis.com\""));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(WebPushCrypto.publicKey(pub));
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(WebPushCrypto.b64(parts[2])));
        assertEquals(WebPushCrypto.b64(pub), h.substring(h.indexOf(", k=") + 4));
    }
}
