package com.legalsuite.service;

import java.util.List;

/**
 * The slice of a matter the signed-in staff member can already open.
 * File bytes and storage paths are never included.
 */
public record MatterPacket(
        String caseId,
        String caseNumber,
        String title,
        String status,
        String practiceArea,
        String caseType,
        String description,
        String clientName,
        String opposingParty,
        String opposingCounsel,
        String courtName,
        String judgeName,
        boolean appearanceAuthorized,
        String engagementStatus,
        boolean docketHold,
        String docketHoldReason,
        List<ClockLine> clocks,
        List<NoteLine> notes,
        List<FileLine> files) {

    public record ClockLine(String kind, String title, String date, String citation, String reason, boolean overdue) {}

    public record NoteLine(String title, String body) {}

    public record FileLine(String id, String name, String category, boolean privileged) {}
}
