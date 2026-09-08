package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DocumentHashTest {
    @Test
    void stableForSameParts() {
        assertEquals(DocumentHash.sha256("body", "Ada"), DocumentHash.sha256("body", "Ada"));
    }

    @Test
    void changesWhenBodyChanges() {
        assertNotEquals(DocumentHash.sha256("a"), DocumentHash.sha256("b"));
    }

    @Test
    void unsignedFileCannotAppear() {
        assertFalse(DocumentHash.appearanceAuthorized("unsigned", false));
        assertTrue(DocumentHash.appearanceAuthorized("signed", false));
        assertTrue(DocumentHash.appearanceAuthorized("not_required", false));
        assertTrue(DocumentHash.appearanceAuthorized("unsigned", true));
    }

    @Test
    void limitedFileMayOnlyCloseOrStay() {
        assertTrue(DocumentHash.statusAllowedWhileLimited("limited"));
        assertTrue(DocumentHash.statusAllowedWhileLimited("declined"));
        assertFalse(DocumentHash.statusAllowedWhileLimited("open"));
        assertFalse(DocumentHash.statusAllowedWhileLimited("trial"));
    }
}
