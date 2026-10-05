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
class VoicePstnTest {
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
    private FirmPhoneNumber line;

    @BeforeEach
    void setUp() {
        TwilioProperties props = new TwilioProperties();
        props.setAccountSid("AC1234567890");
        props.setAuthToken("test-auth-token");
        props.setPublicBaseUrl("https://example.com");
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

    @Test
    void dialRingsTheAttorneyAndShowsTheFirmNumber() {
        when(firmNumbers.requireForDial(lineId)).thenReturn(line);
        LegalCase matter = new LegalCase();
        matter.setId(caseId);
        matter.setClientId(UUID.randomUUID());
        when(cases.findByIdAndTenantId(caseId, tenantId)).thenReturn(Optional.of(matter));
        when(twilio.createCall(any(), any(), any(), any())).thenReturn("CA123");

        Map<String, Object> body = new HashMap<>();
        body.put("to", "082 555 0199");
        body.put("staffCallback", "083 555 0100");
        body.put("caseId", caseId.toString());
        body.put("firmPhoneNumberId", lineId.toString());

        Map<String, Object> view = voice.placePstn(body);

        verify(twilio).createCall(eq("+27835550100"), eq("+27115550100"), contains("/api/v1/voice/twilio/bridge/"), contains("/api/v1/voice/twilio/status/"));
        assertEquals("pstn_outbound", view.get("callType"));
        assertEquals("+27115550100", view.get("fromNumber"));
        assertEquals("+27825550199", view.get("toNumber"));
        assertEquals("+27835550100", view.get("staffCallbackNumber"));
        assertEquals(false, view.get("nonMatter"));
        assertFalse(view.containsKey("bridgeToken"));
        assertFalse(view.toString().contains("test-auth-token"));
    }

    @Test
    void bridgeTwimlDialsTheDestinationWithTheFirmCallerId() {
        when(firmNumbers.requireForDial(lineId)).thenReturn(line);
        LegalCase matter = new LegalCase();
        matter.setId(caseId);
        when(cases.findByIdAndTenantId(caseId, tenantId)).thenReturn(Optional.of(matter));
        when(twilio.createCall(any(), any(), any(), any())).thenReturn("CA123");
        Map<String, Object> body = Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100",
                "caseId", caseId.toString(),
                "firmPhoneNumberId", lineId.toString());
        voice.placePstn(body);

        ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
        verify(calls, times(2)).save(saved.capture());
        CallRecord rec = saved.getValue();
        when(calls.findByBridgeToken(rec.getBridgeToken())).thenReturn(Optional.of(rec));

        String xml = voice.bridgeTwiml(rec.getBridgeToken(), "CA123");
        assertTrue(xml.contains("callerId=\"+27115550100\""));
        assertTrue(xml.contains("<Number>+27825550199</Number>"));
        assertFalse(xml.contains("+27835550100"));
        assertTrue(xml.contains("do-not-record"));
    }

    @Test
    void emergencyNeverReachesTheCarrier() {
        ApiException ex = assertThrows(ApiException.class, () -> voice.placePstn(Map.of(
                "to", "10111",
                "staffCallback", "0835550100",
                "nonMatter", true)));
        assertTrue(ex.getMessage().toLowerCase().contains("device dialer"));
        verify(twilio, never()).createCall(any(), any(), any(), any());
        verify(firmNumbers, never()).requireForDial(any());
    }

    @Test
    void matterIsRequiredUnlessExplicitlyWaived() {
        ApiException ex = assertThrows(ApiException.class, () -> voice.placePstn(Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100")));
        assertTrue(ex.getMessage().toLowerCase().contains("matter"));
        verify(twilio, never()).createCall(any(), any(), any(), any());
    }

    @Test
    void nonMatterCallIsAllowedAndNotBillable() {
        when(firmNumbers.requireForDial(null)).thenReturn(line);
        when(twilio.createCall(any(), any(), any(), any())).thenReturn("CA124");
        Map<String, Object> view = voice.placePstn(Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100",
                "nonMatter", true));
        assertEquals(true, view.get("nonMatter"));
        ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
        verify(calls, times(2)).save(saved.capture());
        assertFalse(saved.getValue().isBillable());
        assertTrue(saved.getValue().getCaseId() == null);
    }

    @Test
    void recordingWithoutConsentIsRejected() {
        ApiException ex = assertThrows(ApiException.class, () -> voice.placePstn(Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100",
                "caseId", caseId.toString(),
                "recordingEnabled", true,
                "recordingConsentGiven", false)));
        assertTrue(ex.getMessage().toLowerCase().contains("consent"));
        verify(twilio, never()).createCall(any(), any(), any(), any());
    }

    @Test
    void completedBridgeWritesOneTimeEntry() {
        when(firmNumbers.requireForDial(lineId)).thenReturn(line);
        LegalCase matter = new LegalCase();
        matter.setId(caseId);
        when(cases.findByIdAndTenantId(caseId, tenantId)).thenReturn(Optional.of(matter));
        when(twilio.createCall(any(), any(), any(), any())).thenReturn("CA125");
        AppUser user = new AppUser();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setHourlyRate(new BigDecimal("400"));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(timeEntries.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        voice.placePstn(Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100",
                "caseId", caseId.toString(),
                "firmPhoneNumberId", lineId.toString()));
        ArgumentCaptor<CallRecord> saved = ArgumentCaptor.forClass(CallRecord.class);
        verify(calls, times(2)).save(saved.capture());
        CallRecord rec = saved.getValue();
        when(calls.findByBridgeToken(rec.getBridgeToken())).thenReturn(Optional.of(rec));

        voice.onDialResult(rec.getBridgeToken(), "completed", "125", null);
        voice.onParentStatus(rec.getBridgeToken(), "completed", "130", null);
        verify(timeEntries, times(1)).save(any());
        assertEquals("completed", rec.getStatus());
        assertEquals(125, rec.getDurationSeconds());
    }

    @Test
    void placeCallWithoutARentedDidUsesTheResolvedCallerId() {
        FirmPhoneNumber fromEnv = new FirmPhoneNumber();
        fromEnv.setTenantId(tenantId);
        fromEnv.setE164("+14155550100");
        fromEnv.setKind(FirmPhoneNumber.KIND_VOICE_FROM);
        fromEnv.setStatus(FirmPhoneNumber.STATUS_ACTIVE);
        when(firmNumbers.requireForDial(null)).thenReturn(fromEnv);
        when(twilio.createCall(any(), any(), any(), any())).thenReturn("CA200");

        Map<String, Object> view = voice.placePstn(Map.of(
                "to", "0825550199",
                "staffCallback", "0835550100",
                "nonMatter", true));

        verify(twilio).createCall(eq("+27835550100"), eq("+14155550100"), contains("/bridge/"), contains("/status/"));
        assertEquals("+14155550100", view.get("fromNumber"));
        assertEquals("voice_from", view.get("callerIdKind"));
        assertEquals(null, view.get("firmPhoneNumberId"));
        assertEquals(true, view.get("nonMatter"));
    }
}
