package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.OutboundMessage;
import com.legalsuite.domain.Tenant;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.mail.MessagingProperties;
import com.legalsuite.mail.SmtpProperties;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.ContactRepository;
import com.legalsuite.repo.FirmPhoneNumberRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.OutboundMessageRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioMessage;
import com.legalsuite.voice.TwilioProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
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
class OutboundMessageServiceTest {
    @Mock OutboundMessageRepository messages;
    @Mock LegalCaseRepository cases;
    @Mock ClientRepository clients;
    @Mock ContactRepository contacts;
    @Mock FirmPhoneNumberRepository numbers;
    @Mock TenantRepository tenants;
    @Mock IntegrationService integrations;
    @Mock AuditService audit;
    @Mock TwilioGateway twilio;
    @Mock MailGateway mail;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID caseId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private TwilioProperties props;
    private OutboundMessageService service;

    @BeforeEach
    void setUp() {
        props = new TwilioProperties();
        props.setAccountSid("AC1234567890");
        props.setAuthToken("super-secret-token");
        props.setSmsFrom("+27115550100");
        SmtpProperties smtp = new SmtpProperties();
        service = new OutboundMessageService(
                messages, cases, clients, contacts, numbers, tenants, integrations, audit,
                twilio, props, mail, smtp, new MessagingProperties(), new ObjectMapper());
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        TenantContext.setRole("attorney");

        Tenant tenant = new Tenant();
        tenant.setCountry("ZA");
        tenant.setFirmName("Ndlovu & Partners");
        lenient().when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        lenient().when(messages.save(any())).thenAnswer(inv -> {
            OutboundMessage row = inv.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID());
            return row;
        });
        lenient().when(integrations.isConnected("twilio")).thenReturn(true);
        lenient().when(numbers.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void missingTwilioCredentialsAreAClearErrorAndAreAudited() {
        props.setAccountSid("");
        props.setAuthToken("");
        Map<String, Object> body = Map.of("to", "082 555 0144", "body", "Please call the office.");

        ApiException ex = assertThrows(ApiException.class, () -> service.sendSms(body));

        assertTrue(ex.getMessage().contains("TWILIO_ACCOUNT_SID"));
        assertFalse(ex.getMessage().contains("super-secret-token"));
        verify(twilio, never()).sendMessage(any());
        OutboundMessage saved = lastSaved();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals("failed", saved.getStatus());
        verify(audit).record(eq("outbound.sms.failed"), eq("outbound"), eq(saved.getId().toString()), eq(ex.getMessage()));
    }

    @Test
    void emergencySmsIsRefusedBeforeTwilio() {
        ApiException ex = assertThrows(ApiException.class, () -> service.sendSms(Map.of("to", "10111", "body", "Help")));

        assertEquals(OutboundMessageService.EMERGENCY, ex.getMessage());
        verify(twilio, never()).sendMessage(any());
        OutboundMessage saved = lastSaved();
        assertEquals("refused", saved.getStatus());
        assertEquals(tenantId, saved.getTenantId());
        verify(audit).record(eq("outbound.sms.refused"), eq("outbound"), eq(saved.getId().toString()), eq(OutboundMessageService.EMERGENCY));
    }

    @Test
    void internationalEmergencyFormIsAlsoRefused() {
        assertThrows(ApiException.class, () -> service.sendSms(Map.of("to", "+27 10111", "body", "Help")));
        verify(twilio, never()).sendMessage(any());
        assertEquals("refused", lastSaved().getStatus());
    }

    @Test
    void anotherFirmsMatterIsNotSent() {
        UUID foreign = UUID.randomUUID();
        when(cases.findByIdAndTenantId(foreign, tenantId)).thenReturn(Optional.empty());
        Map<String, Object> body = new HashMap<>();
        body.put("to", "0825550144");
        body.put("body", "Status update");
        body.put("caseId", foreign.toString());

        ApiException ex = assertThrows(ApiException.class, () -> service.sendSms(body));

        assertEquals("That matter is not on this firm.", ex.getMessage());
        verify(cases).findByIdAndTenantId(foreign, tenantId);
        verify(cases, never()).findByIdAndTenantId(foreign, otherTenant);
        verify(twilio, never()).sendMessage(any());
        assertEquals("failed", lastSaved().getStatus());
        assertEquals(tenantId, lastSaved().getTenantId());
    }

    @Test
    void listUsesTheSignedInTenantOnly() {
        OutboundMessage row = new OutboundMessage();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setChannel("sms");
        row.setStatus("sent");
        row.setToAddress("+27825550144");
        when(messages.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(row));
        when(messages.findByTenantIdOrderByCreatedAtDesc(otherTenant)).thenReturn(List.of());

        assertEquals(1, service.list(null).size());
        TenantContext.setTenantId(otherTenant);
        assertTrue(service.list(null).isEmpty());
        verify(messages).findByTenantIdOrderByCreatedAtDesc(otherTenant);
    }

    @Test
    void blankSmsAndBadEmailAreRejected() {
        ApiException blank = assertThrows(ApiException.class, () -> service.sendSms(Map.of("to", "0825550144", "body", "  ")));
        assertEquals("Enter a message.", blank.getMessage());

        ApiException email = assertThrows(ApiException.class, () -> service.sendEmail(Map.of(
                "to", "not-an-email",
                "subject", "Update",
                "body", "Hello")));
        assertEquals("That email address is not valid.", email.getMessage());
        verify(twilio, never()).sendMessage(any());
        verify(mail, never()).send(any(), any(), any(), any());
    }

    @Test
    void smsUsesE164AndRecordsTheUnitCost() {
        LegalCase matter = new LegalCase();
        matter.setId(caseId);
        matter.setClientId(clientId);
        when(cases.findByIdAndTenantId(caseId, tenantId)).thenReturn(Optional.of(matter));
        when(twilio.sendMessage(any())).thenReturn("SM123");

        Map<String, Object> body = new HashMap<>();
        body.put("to", "082 555 0144");
        body.put("body", "Your consultation is confirmed.");
        body.put("caseId", caseId.toString());
        Map<String, Object> view = service.sendSms(body);

        ArgumentCaptor<TwilioMessage> sent = ArgumentCaptor.forClass(TwilioMessage.class);
        verify(twilio).sendMessage(sent.capture());
        assertEquals("+27825550144", sent.getValue().to());
        assertEquals("+27115550100", sent.getValue().from());
        assertFalse(sent.getValue().to().contains("super-secret-token"));
        assertEquals("sent", view.get("status"));
        assertEquals(caseId, view.get("caseId"));
        assertEquals(clientId, view.get("clientId"));
        assertEquals(new BigDecimal("0.08"), view.get("unitCost"));
        assertFalse(view.containsKey("callbackToken"));
        verify(audit).record(eq("outbound.sms.sent"), eq("outbound"), any(), eq("+27825550144"));
    }

    @Test
    void whatsappUsesTheSandboxPrefixAndATemplate() {
        when(twilio.sendMessage(any())).thenReturn("SM124");
        String contentSid = "HX" + "ab".repeat(16);
        Map<String, Object> body = new HashMap<>();
        body.put("to", "0825550144");
        body.put("contentSid", contentSid);
        body.put("contentVariables", "{\"1\":\"Nomsa\"}");

        Map<String, Object> view = service.sendWhatsapp(body);

        ArgumentCaptor<TwilioMessage> sent = ArgumentCaptor.forClass(TwilioMessage.class);
        verify(twilio).sendMessage(sent.capture());
        assertEquals("whatsapp:+27825550144", sent.getValue().to());
        assertEquals("whatsapp:+14155238886", sent.getValue().from());
        assertEquals(contentSid, sent.getValue().contentSid());
        assertEquals("", sent.getValue().body());
        assertEquals("sent", view.get("status"));
        assertEquals(new BigDecimal("0.05"), view.get("unitCost"));
    }

    @Test
    void emailWithoutSmtpIsADryRunOnThisTenant() {
        when(mail.configured()).thenReturn(false);
        when(mail.unconfiguredReason()).thenReturn("SMTP is not configured. This message was logged on the firm and was not delivered.");
        Client client = new Client();
        client.setId(clientId);
        client.setEmail("nomsa@example.com");
        when(clients.findByIdAndTenantId(clientId, tenantId)).thenReturn(Optional.of(client));

        Map<String, Object> view = service.sendEmail(Map.of(
                "clientId", clientId.toString(),
                "subject", "Consultation",
                "body", "Please see the attached note."));

        assertEquals("dry_run", view.get("status"));
        assertEquals("nomsa@example.com", view.get("to"));
        assertTrue(String.valueOf(view.get("notice")).contains("SMTP"));
        verify(mail, never()).send(any(), any(), any(), any());
        verify(audit).record(eq("outbound.email.dry_run"), eq("outbound"), any(), eq("nomsa@example.com"));
        assertEquals(tenantId, lastSaved().getTenantId());
    }

    @Test
    void carrierFailureDoesNotEchoTheAuthToken() {
        when(twilio.sendMessage(any())).thenThrow(new RuntimeException("Twilio said super-secret-token"));

        ApiException ex = assertThrows(ApiException.class, () -> service.sendSms(Map.of("to", "0825550144", "body", "Hello")));

        assertFalse(ex.getMessage().contains("super-secret-token"));
        assertTrue(ex.getMessage().contains("[redacted]"));
        assertFalse(lastSaved().getErrorMessage().contains("super-secret-token"));
    }

    private OutboundMessage lastSaved() {
        ArgumentCaptor<OutboundMessage> saved = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(messages, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }
}
