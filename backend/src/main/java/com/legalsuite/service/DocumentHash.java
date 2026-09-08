package com.legalsuite.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of a signed instrument. The hash lives on the tenant with the document. */
public final class DocumentHash {
    private DocumentHash() {}

    public static String sha256(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) md.update((byte) 0);
                md.update((parts[i] == null ? "" : parts[i]).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    public static boolean appearanceAuthorized(String engagementStatus, boolean flag) {
        if (flag) return true;
        return "signed".equals(engagementStatus) || "not_required".equals(engagementStatus);
    }

    public static boolean statusAllowedWhileLimited(String status) {
        return status != null && java.util.Set.of("limited", "declined", "closed").contains(status);
    }
}
