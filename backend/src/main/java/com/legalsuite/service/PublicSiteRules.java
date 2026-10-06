package com.legalsuite.service;

/** A feature is on the public site only when the firm asked for it, a platform operator approved it, and the site is published. */
public final class PublicSiteRules {
    private PublicSiteRules() {}

    public static boolean live(String publishStatus, boolean requested, String approvalStatus) {
        return "published".equals(publishStatus) && requested && "approved".equals(approvalStatus);
    }
}
