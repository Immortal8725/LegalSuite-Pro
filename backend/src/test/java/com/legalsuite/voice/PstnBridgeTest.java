package com.legalsuite.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PstnBridgeTest {
    @Test
    void secondLegShowsTheFirmCallerId() {
        String xml = PstnBridge.twiml(
                "+27115550100",
                "+27825550199",
                "https://example.com/api/v1/voice/twilio/dial-result/abc",
                false,
                null);
        assertTrue(xml.contains("callerId=\"+27115550100\""));
        assertTrue(xml.contains("<Number>+27825550199</Number>"));
        assertTrue(xml.contains("record=\"do-not-record\""));
        assertFalse(xml.contains("+27830000000"));
        assertFalse(xml.contains("—"));
    }

    @Test
    void recordingIsOptIn() {
        String xml = PstnBridge.twiml("+14155550100", "+14155550199", "https://example.com/dial", true, "https://example.com/rec");
        assertTrue(xml.contains("record=\"record-from-answer\""));
        assertTrue(xml.contains("recordingStatusCallback=\"https://example.com/rec\""));
    }
}
