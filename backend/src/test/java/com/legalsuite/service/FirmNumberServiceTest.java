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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.FirmPhoneNumber;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.FirmPhoneNumberRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirmNumberServiceTest {
    @Mock FirmPhoneNumberRepository numbers;
    @Mock TenantRepository tenants;
    @Mock TwilioGateway twilio;
    @Mock IntegrationService integrations;
    @Mock AuditService audit;

    private final UUID tenantId = UUID.randomUUID();
    private FirmNumberService service;
    private TwilioProperties props;

    @BeforeEach
    void setUp() {
        props = new TwilioProperties();
        props.setAccountSid("AC1234567890");
        props.setAuthToken("secret-token");
        props.setPublicBaseUrl("https://pbx.example.com");
        service = new FirmNumberService(numbers, tenants, twilio, props, integrations, audit);
        TenantContext.setTenantId(tenantId);
        TenantContext.setRole("director");
        lenient().when(integrations.isConnected("twilio")).thenReturn(true);
        Tenant tenant = new Tenant();
        tenant.setCountry("ZA");
        tenant.setFirmName("Ndlovu & Partners");
        lenient().when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        lenient().when(numbers.save(any())).thenAnswer(inv -> {
            FirmPhoneNumber row = inv.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID());
            return row;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void anotherFirmsCallerIdIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(numbers.findByIdAndTenantId(foreign, tenantId)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.requireForDial(foreign));
        assertEquals(404, ex.getStatus().value());
        assertFalse(ex.getMessage().contains(foreign.toString()));
    }

    @Test
    void pendingLandlineCannotBeUsedAsCallerId() {
        FirmPhoneNumber row = landline(FirmPhoneNumber.STATUS_PENDING);
        when(numbers.findByIdAndTenantId(row.getId(), tenantId)).thenReturn(Optional.of(row));
        ApiException ex = assertThrows(ApiException.class, () -> service.requireForDial(row.getId()));
        assertTrue(ex.getMessage().toLowerCase().contains("verified"));
    }

    @Test
    void verifiedLandlineCanBeTheCallerId() {
        FirmPhoneNumber row = landline(FirmPhoneNumber.STATUS_ACTIVE);
        when(numbers.findByIdAndTenantId(row.getId(), tenantId)).thenReturn(Optional.of(row));
        FirmPhoneNumber ready = service.requireForDial(row.getId());
        assertEquals(FirmPhoneNumber.KIND_LANDLINE, ready.getKind());
        assertEquals("+27115550123", ready.getE164());
    }

    @Test
    void buyStoresNoSecretAndMarksTheDidActive() {
        when(numbers.findByE164AndStatusIn(any(), any())).thenReturn(List.of());
        when(numbers.findFirstByTenantIdAndDefaultOutboundTrueAndStatus(any(), any())).thenReturn(Optional.empty());
        when(twilio.buyLocal(any(), any(), any(), any())).thenReturn(Map.of("sid", "PN1234567890", "phoneNumber", "+27115550199"));

        Map<String, Object> view = service.buy(Map.of("phoneNumber", "+27115550199", "locality", "Sandton"));

        assertEquals("did", view.get("kind"));
        assertEquals("active", view.get("status"));
        assertEquals("+27115550199", view.get("e164"));
        assertFalse(view.containsKey("twilioSid"));
        assertFalse(view.toString().contains("secret-token"));
        verify(twilio).buyLocal(eq("+27115550199"), any(), contains("/api/v1/voice/twilio/inbound"), contains("/inbound-status"));
    }

    @Test
    void verificationStaysPendingUntilTheCarrierConfirms() {
        when(numbers.findByE164AndStatusIn(any(), any())).thenReturn(List.of());
        when(twilio.startCallerIdVerification(any(), any(), any()))
                .thenReturn(Map.of("validationCode", "846291", "callSid", "CA1", "phoneNumber", "+27115550123"));

        Map<String, Object> view = service.startVerification(Map.of("phoneNumber", "011 555 0123", "friendlyName", "Sandton office"));
        assertEquals("pending", view.get("status"));
        assertEquals("verified_landline", view.get("kind"));
        assertEquals("846291", view.get("validationCode"));

        FirmPhoneNumber pending = landline(FirmPhoneNumber.STATUS_PENDING);
        pending.setValidationCode("846291");
        when(numbers.findByIdAndTenantId(pending.getId(), tenantId)).thenReturn(Optional.of(pending));
        when(twilio.findVerifiedCallerIdSid("+27115550123")).thenReturn("PNverified1");
        when(numbers.findFirstByTenantIdAndDefaultOutboundTrueAndStatus(any(), any())).thenReturn(Optional.empty());

        Map<String, Object> ready = service.refresh(pending.getId());
        assertEquals("active", ready.get("status"));
        assertFalse(ready.containsKey("validationCode"));
    }

    @Test
    void numberHeldByAnotherFirmIsRejectedWithoutNamingThem() {
        FirmPhoneNumber foreign = new FirmPhoneNumber();
        foreign.setTenantId(UUID.randomUUID());
        foreign.setE164("+27115550199");
        when(numbers.findByE164AndStatusIn(eq("+27115550199"), any())).thenReturn(List.of(foreign));
        ApiException ex = assertThrows(ApiException.class, () -> service.buy(Map.of("phoneNumber", "+27115550199")));
        assertFalse(ex.getMessage().contains(foreign.getTenantId().toString()));
        verify(twilio, never()).buyLocal(any(), any(), any(), any());
    }

    @Test
    void emergencyCannotBeRented() {
        ApiException ex = assertThrows(ApiException.class, () -> service.buy(Map.of("phoneNumber", "10111")));
        assertTrue(ex.getMessage().toLowerCase().contains("device dialer"));
        verify(twilio, never()).buyLocal(any(), any(), any(), any());
    }

    @Test
    void rentedDidWinsOverAVerifiedPersonalNumber() {
        FirmPhoneNumber personal = landline(FirmPhoneNumber.STATUS_ACTIVE);
        personal.setDefaultOutbound(true);
        FirmPhoneNumber did = new FirmPhoneNumber();
        did.setId(UUID.randomUUID());
        did.setTenantId(tenantId);
        did.setE164("+27115550999");
        did.setKind(FirmPhoneNumber.KIND_DID);
        did.setStatus(FirmPhoneNumber.STATUS_ACTIVE);
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(personal, did));

        FirmPhoneNumber chosen = service.requireForDial(null);

        assertEquals("+27115550999", chosen.getE164());
        verify(twilio, never()).firstIncomingNumber();
        verify(twilio, never()).buyLocal(any(), any(), any(), any());
    }

    @Test
    void verifiedPersonalNumberIsEnoughWithoutARentedDid() {
        FirmPhoneNumber personal = landline(FirmPhoneNumber.STATUS_ACTIVE);
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(personal));

        FirmPhoneNumber chosen = service.requireForDial(null);

        assertEquals("+27115550123", chosen.getE164());
        assertEquals(FirmPhoneNumber.KIND_LANDLINE, chosen.getKind());
        verify(twilio, never()).firstIncomingNumber();
        verify(twilio, never()).buyLocal(any(), any(), any(), any());
    }

    @Test
    void voiceFromIsUsedWhenTheFirmHasNoSavedNumber() {
        props.setVoiceFrom("+14155550100");
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());

        FirmPhoneNumber chosen = service.requireForDial(null);

        assertEquals("+14155550100", chosen.getE164());
        assertEquals(FirmPhoneNumber.KIND_VOICE_FROM, chosen.getKind());
        assertEquals(null, chosen.getId());
        verify(twilio, never()).firstIncomingNumber();
        verify(twilio, never()).buyLocal(any(), any(), any(), any());
    }

    @Test
    void accountIncomingIsUsedWhenVoiceFromIsUnset() {
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(twilio.firstIncomingNumber()).thenReturn("+14155550111");

        FirmPhoneNumber chosen = service.requireForDial(null);

        assertEquals("+14155550111", chosen.getE164());
        assertEquals(FirmPhoneNumber.KIND_ACCOUNT_INCOMING, chosen.getKind());
        verify(twilio, never()).firstOutgoingCallerId();
    }

    @Test
    void accountOutgoingCallerIdIsTheLastAutomaticSource() {
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(twilio.firstIncomingNumber()).thenReturn(null);
        when(twilio.firstOutgoingCallerId()).thenReturn("+14155550122");

        assertEquals(FirmPhoneNumber.KIND_ACCOUNT_OUTGOING, service.requireForDial(null).getKind());
        assertEquals("+14155550122", service.requireForDial(null).getE164());
    }

    @Test
    void missingCallerIdDoesNotAskForAPurchaseOrABundle() {
        when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(twilio.firstIncomingNumber()).thenReturn("");
        when(twilio.firstOutgoingCallerId()).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.requireForDial(null));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("twilio_voice_from"));
        assertTrue(msg.contains("personal"));
        assertFalse(msg.contains("bundle"));
        assertFalse(msg.contains("regulatory"));
        assertFalse(msg.contains("buy"));
        assertFalse(msg.contains("rent"));
    }

    private FirmPhoneNumber landline(String status) {
        FirmPhoneNumber row = new FirmPhoneNumber();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setE164("+27115550123");
        row.setKind(FirmPhoneNumber.KIND_LANDLINE);
        row.setStatus(status);
        return row;
    }
}
