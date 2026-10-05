package com.legalsuite.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TwilioSignatureTest {
    @Test
    void matchesTwilioCanonicalString() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("From", new String[] {"+15551212"});
        params.put("CallSid", new String[] {"CA123"});
        String url = "https://example.com/api/v1/voice/twilio/bridge/abc";
        assertTrue(TwilioSignature.valid("test-auth-token", url, params, "1VHz+ZNdKX+GVZBOZYWfrCL/xuY="));
    }

    @Test
    void rejectsATamperedBodyOrMissingToken() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("CallSid", new String[] {"CA999"});
        String url = "https://example.com/api/v1/voice/twilio/bridge/abc";
        assertFalse(TwilioSignature.valid("test-auth-token", url, params, "1VHz+ZNdKX+GVZBOZYWfrCL/xuY="));
        assertFalse(TwilioSignature.valid("", url, params, "1VHz+ZNdKX+GVZBOZYWfrCL/xuY="));
        assertFalse(TwilioSignature.valid("test-auth-token", url, params, ""));
    }
}
