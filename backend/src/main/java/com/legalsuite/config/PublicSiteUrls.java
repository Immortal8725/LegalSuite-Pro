package com.legalsuite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds a firm's public site address: https://<slug>.<root> when a root is configured, else /firm/<slug>. */
@Component
public class PublicSiteUrls {
    private static volatile String root = "";

    public PublicSiteUrls(@Value("${legalsuite.public-site-root:}") String configured) {
        root = clean(configured);
    }

    public static String clean(String raw) {
        String r = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        r = r.replaceFirst("^https?://", "").replaceAll("/+$", "");
        // Only a plain domain name is accepted, so the CORS pattern built from it stays narrow.
        return r.matches("^[a-z0-9-]+(\\.[a-z0-9-]+)+$") ? r : "";
    }

    public static String root() {
        return root;
    }

    public static String of(String slug) {
        return root.isEmpty() ? "/firm/" + slug : "https://" + slug + "." + root;
    }

    /** For tests. */
    public static void setRoot(String r) {
        root = clean(r);
    }
}
