package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.FirmPhoneNumber;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceAppCallTest {
    @Mock CallRecordRepository calls;
    @Mock TimeEntryRepository timeEntries;
    @Mock AppUserRepository users;
    @Mock TenantRepository tenants;
    @Mock NoteRepository notes;
    @Mock FirmNumberService firmNumbers;
    @Mock LegalCaseRepository cases;
    @Mock TwilioGateway twilio;
    @Mock AuditService audit;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();
    private VoiceService voice;
    private TwilioProperties props;
    private FirmPhoneNumber line;

    @BeforeEach
    void setUp() {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("AC1234567890");
        props.setAuthToken("test-auth-token");
        props.setPublicBaseUrl("https://example.com");
        props.setApiKeySid("SK0123456789abcdef");
        props.setApiKeySecret("0123456789abcdef0123456789abcdef");
        props.setTwimlAppSid("AP0123456789abcdef");
        this.props = props;
        voice = new VoiceService(calls, timeEntries, users, tenants, notes, firmNumbers, cases, twilio, props, audit, "", "");
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        TenantContext.setRole("attorney");

        Tenant tenant = new Tenant();
        tenant.setCountry("ZA");
        tenant.setFirmName("Ndlovu & Partners");
        lenient().when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        line = new FirmPhoneNumber();
        line.setId(lineId);
        line.setTenantId(tenantId);
        line.setE164("+27115550100");
        line.setKind(FirmPhoneNumber.KIND_DID);
        line.setStatus(FirmPhoneNumber.STATUS_ACTIVE);

        lenient().when(calls.save(any())).thenAnswer(inv -> {
            CallRecord row = inv.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID());
            return row;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Checks the HS256 signature by hand (jjwt reads Twilio's cty header as a content type) and returns the payload. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
            assertEquals(sig, parts[2]);
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> header = json.readValue(java.util.Base64.getUrlDecoder().decode(parts[0]), Map.class);
            assertEquals("twilio-fpa;v=1", header.get("cty"));
            assertEquals("HS256", header.get("alg"));
            return json.readValue(java.util.Base64.getUrlDecoder().decode(parts[1]), Map.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenIsASignedTwilioVoiceGrantForThisUser() {
        Map<String, Object> t = voice.appToken();
        Map<String, Object> c = parse((String) t.get("token"));
        assertEquals("SK0123456789abcdef", c.get("iss"));
        assertEquals("AC1234567890", c.get("sub"));
        assertTrue(((Number) c.get("exp")).longValue() > ((Number) c.get("iat")).longValue());
        Map<String, Object> grants = (Map<String, Object>) c.get("grants");
        assertEquals(VoiceService.appIdentity(userId), grants.get("identity"));
        Map<String, Object> v = (Map<String, Object>) grants.get("voice");
        assertEquals("AP0123456789abcdef", ((Map<String, Object>) v.get("outgoing")).get("application_sid"));
        assertEquals(false, t.get("incomingReady"));
        assertFalse(t.toString().contains("0123456789abcdef0123456789abcdef"));
        assertTrue(VoiceService.appIdentity(userId).matches("[A-Za-z0-9_]+"));
    }

    @Test
    void tokenIsRefusedWhenInAppCallingIsNotSetUp() {
        props.setTwimlAppSid("");
        ApiException e = assertThrows(ApiException.class, () -> voice.appToken());
        assertTrue(e.getMessage().contains("callback call"));
    }

    @Test
    void appCallIsLoggedToTheMatterAndOnlyItsOwnerCanDial() {
        when(firmNumbers.requireForDial(lineId)).thenReturn(line);
        LegalCase matter = new LegalCase();
        matter.setId(caseId);
        UUID client = UUID.randomUUID();
        matter.setClientId(client);
        when(cases.findByIdAndTenantId(caseId, tenantId)).thenReturn(Optional.of(matter));
        Map<String, Object> view = voice.placeAppCall(Map.of("to", "082 555 0199", "caseId", caseId.toString(), "firmPhoneNumberId", lineId.toString()));
        assertEquals("app_outbound", view.get("callType"));
        assertEquals("+27825550199", view.get("toNumber"));
        assertTrue(view.containsKey("accessToken"));
        String token = (String) view.get("bridgeToken");

        ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
        verify(calls).save(saved.capture());
        CallRecord rec = saved.getValue();
        assertEquals(caseId, rec.getCaseId());
        assertEquals(client, rec.getClientId());
        verify(twilio, never()).createCall(any(), any(), any(), any());
        when(calls.findByBridgeToken(token)).thenReturn(Optional.of(rec));

        assertThrows(ApiException.class, () -> voice.appDialTwiml(token, "client:owlaw_someone_else", "CA1"));
        String twiml = voice.appDialTwiml(token, "client:" + VoiceService.appIdentity(userId), "CA1");
        assertTrue(twiml.contains("+27825550199"));
        assertTrue(twiml.contains("callerId=\"+27115550100\""));
        assertTrue(twiml.contains("/api/v1/voice/twilio/dial-result/" + token));
    }

    @Test
    void appCallKeepsTheMatterAndEmergencyRules() {
        assertThrows(ApiException.class, () -> voice.placeAppCall(Map.of("to", "0825550199")));
        assertThrows(ApiException.class, () -> voice.placeAppCall(Map.of("to", "10111", "nonMatter", true)));
    }
}
