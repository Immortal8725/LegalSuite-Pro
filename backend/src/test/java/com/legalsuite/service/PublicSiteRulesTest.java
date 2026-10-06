package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublicSiteRulesTest {
    @Test
    void liveOnlyWhenPublishedRequestedAndApproved() {
        assertTrue(PublicSiteRules.live("published", true, "approved"));
    }

    @Test
    void requestedButUnapprovedStaysOff() {
        assertFalse(PublicSiteRules.live("published", true, "pending"));
        assertFalse(PublicSiteRules.live("published", true, "rejected"));
        assertFalse(PublicSiteRules.live("published", true, "none"));
    }

    @Test
    void unpublishedSiteHidesApprovedFeatures() {
        assertFalse(PublicSiteRules.live("draft", true, "approved"));
        assertFalse(PublicSiteRules.live("pending_approval", true, "approved"));
        assertFalse(PublicSiteRules.live("rejected", true, "approved"));
    }

    @Test
    void turningAFeatureOffDoesNotNeedASecondApproval() {
        assertFalse(PublicSiteRules.live("published", false, "approved"));
    }
}
