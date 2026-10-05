package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatterAssistantTest {
    private final MatterPacket raf = new MatterPacket(
            "case-1",
            "C-2001",
            "Khumalo v Road Accident Fund",
            "open",
            "RAF",
            "RAF",
            "N12 collision.",
            "Nomsa Khumalo",
            "Road Accident Fund",
            "",
            "Johannesburg High Court",
            "",
            true,
            "not_required",
            false,
            null,
            List.of(new MatterPacket.ClockLine(
                    "raf_lodge",
                    "Lodge RAF 1 claim",
                    "2026-10-23",
                    "Road Accident Fund Act 56 of 1996 s 23",
                    "Identified vehicle: the right to claim from the Fund prescribes three years from the cause of action (RAF Act s 23(1)).",
                    false)),
            List.of(new MatterPacket.NoteLine("RAF 1 pack", "Hospital records requested. Do not let s 23 run.")),
            List.of(new MatterPacket.FileLine("doc-1", "raf1-pack.pdf", "pleadings", true)));

    @Test
    void clocksAndNotesStayOnTheMatter() {
        String reply = MatterAssistant.answer(
                "What statutory clocks apply, and what do the notes say about the hospital?", raf);
        assertTrue(reply.contains("Road Accident Fund Act 56 of 1996 s 23"));
        assertTrue(reply.contains("2026-10-23"));
        assertTrue(reply.contains("Hospital records requested"));
        assertTrue(reply.contains("attorney remains responsible"));
        assertFalse(reply.contains("seed://"));
    }

    @Test
    void doesNotInventCaseLaw() {
        String reply = MatterAssistant.answer("Cite the leading SCA case that extends prescription on this RAF claim.", raf);
        assertTrue(reply.toLowerCase().contains("will not invent"));
        assertTrue(reply.contains("Road Accident Fund Act 56 of 1996 s 23"));
        assertFalse(reply.contains("2019 (1) SA"));
        assertFalse(reply.contains("Smith v Jones"));
    }

    @Test
    void fileAnswerIsMetadataOnly() {
        String reply = MatterAssistant.answer("Which files are on this matter?", raf);
        assertTrue(reply.contains("raf1-pack.pdf"));
        assertTrue(reply.contains("privileged"));
        assertFalse(reply.contains("storage"));
    }

    @Test
    void guardFlagsCitationsThatAreNotOnTheFile() {
        String guarded = MatterAssistant.guardCitations(
                "See Smith v Jones 2019 (1) SA 12 for the principle.", raf);
        assertTrue(guarded.contains("Smith v Jones"));
        assertTrue(guarded.contains("2019 (1) SA 12"));
        assertTrue(guarded.contains("Do not treat them as authority"));
    }

    @Test
    void guardLeavesTheMatterTitleAlone() {
        String guarded = MatterAssistant.guardCitations(
                "The style is Khumalo v Road Accident Fund. The clock is Road Accident Fund Act 56 of 1996 s 23.",
                raf);
        assertFalse(guarded.contains("Do not treat them as authority"));
    }
}
