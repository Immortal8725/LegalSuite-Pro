package com.legalsuite.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push message encryption (RFC 8291, aes128gcm per RFC 8188) and VAPID (RFC 8292) with the JDK only.
 * No paid service and no extra library: the browser's own push service delivers the message.
 */
public final class WebPushCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    private WebPushCrypto() {}

    public static ECParameterSpec p256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] b64(String s) {
        String t = s.trim().replace('+', '-').replace('/', '_');
        while (t.endsWith("=")) t = t.substring(0, t.length() - 1);
        return D64.decode(t);
    }

    public static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) throw new GeneralSecurityException("Bad P-256 public key");
        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(uncompressed, 1, x, 0, 32);
        System.arraycopy(uncompressed, 33, y, 0, 32);
        ECPoint w = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, p256()));
    }

    public static ECPrivateKey privateKey(byte[] d) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), p256()));
    }

    public static byte[] encode(ECPublicKey k) {
        byte[] out = new byte[65];
        out[0] = 4;
        put32(out, 1, k.getW().getAffineX());
        put32(out, 33, k.getW().getAffineY());
        return out;
    }

    private static void put32(byte[] out, int off, BigInteger v) {
        byte[] b = v.toByteArray();
        int start = Math.max(0, b.length - 32);
        int len = b.length - start;
        System.arraycopy(b, start, out, off + 32 - len, len);
    }

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return g.generateKeyPair();
    }

    static byte[] hmac(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) mac.update(p);
        return mac.doFinal();
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) b.put(p);
        return b.array();
    }

    static byte[] first(byte[] b, int n) {
        byte[] out = new byte[n];
        System.arraycopy(b, 0, out, 0, n);
        return out;
    }

    /** Encrypts {@code payload} for one subscription. Returns the full aes128gcm body. */
    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        return encrypt(payload, uaPublic, authSecret, newKeyPair(), randomSalt());
    }

    static byte[] randomSalt() {
        byte[] s = new byte[16];
        RANDOM.nextBytes(s);
        return s;
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair as, byte[] salt) throws GeneralSecurityException {
        if (payload.length > 3800) throw new GeneralSecurityException("Push payload too large");
        byte[] asPublic = encode((ECPublicKey) as.getPublic());
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(as.getPrivate());
        ka.doPhase(publicKey(uaPublic), true);
        byte[] ecdh = ka.generateSecret();
        byte[] prkKey = hmac(authSecret, ecdh);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[] {1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = first(hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 16);
        byte[] nonce = first(hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] cipher = c.doFinal(concat(payload, new byte[] {2}));
        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + 65);
        header.put(salt).putInt(4096).put((byte) 65).put(asPublic);
        return concat(header.array(), cipher);
    }

    /** VAPID Authorization header value for an endpoint's origin. */
    public static String vapidHeader(String endpoint, String subject, byte[] publicKey, ECPrivateKey privateKey, long nowSeconds)
            throws GeneralSecurityException {
        java.net.URI u = java.net.URI.create(endpoint);
        String aud = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + aud + "\",\"exp\":" + (nowSeconds + 12 * 3600) + ",\"sub\":\"" + subject.replace("\"", "") + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(privateKey);
        s.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + b64(s.sign());
        return "vapid t=" + jwt + ", k=" + b64(publicKey);
    }
}
