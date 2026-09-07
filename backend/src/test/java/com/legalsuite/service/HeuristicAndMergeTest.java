package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MergeEngineTest {
    @Test
    void substitutesTokensAndLeavesUnknownBlank() {
        String out = MergeEngine.merge(
                "Dear {{client.name}}, re {{case.title}} — {{missing}}end",
                Map.of("client.name", "Sarah Williams", "case.title", "Johnson v. Corp Inc."));
        assertEquals("Dear Sarah Williams, re Johnson v. Corp Inc. — end", out);
    }
}

class HeuristicAiTest {
    @Test
    void screensUrgentInjuryHot() {
        Map<String, Object> r = HeuristicAi.screenIntake("Ada Cole", "Personal Injury", "Car crash yesterday, urgent");
        assertTrue((int) r.get("score") >= 70);
        assertEquals("retain_now", r.get("band"));
    }

    @Test
    void summarizesEmptyNotes() {
        String s = HeuristicAi.summarize("Martinez Estate", List.of());
        assertTrue(s.contains("No notes"));
    }

    @Test
    void draftsRetainer() {
        String draft = HeuristicAi.draftEmail("retainer", "Sarah Williams", "Johnson v. Corp Inc.", "");
        assertTrue(draft.contains("Engagement letter"));
        assertTrue(draft.contains("Sarah Williams"));
    }
}
