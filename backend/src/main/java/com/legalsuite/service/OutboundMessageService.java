package com.legalsuite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Contact;
import com.legalsuite.domain.FirmPhoneNumber;
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
import com.legalsuite.voice.E164;
import com.legalsuite.voice.EmergencyNumbers;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioMessage;
import com.legalsuite.voice.TwilioProperties;
import com.legalsuite.voice.TwilioSignature;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboundMessageService {
    static final String EMERGENCY =
            "Emergency numbers stay on the device dialer. This app will not send that message.";
    static final String MISSING_TWILIO =
            "Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in the server environment. Do not paste them into the app.";
    static final String CONNECT_TWILIO =
            "Connect Twilio on the Integrations page before sending SMS or WhatsApp. The toggle does not store a password.";
    static final String MISSING_SMS_FROM =
            "Set TWILIO_SMS_FROM to a Twilio number that can send SMS, or rent a local number on the voice page. A verified landline cannot send text messages.";
    static final String ATTORNEY =
            "You remain responsible for what you send to a client. Check the number or address, the matter, and the wording before you send. This does not file anything at court.";

    private static final Logger log = LoggerFactory.getLogger(OutboundMessageService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[A-Za-z]{2,24}$");
    private static final Pattern CONTENT_SID = Pattern.compile("^HX[0-9a-fA-F]{32}$");
    private static final Pattern MG_SID = Pattern.compile("^MG[0-9a-fA-F]{32}$");
    private static final int SMS_MAX = 1600;
    private static final int WHATSAPP_MAX = 4096;
    private static final int EMAIL_MAX = 20000;

    private final OutboundMessageRepository messages;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final FirmPhoneNumberRepository numbers;
    private final TenantRepository tenants;
    private final IntegrationService integrations;
    private final AuditService audit;
    private final TwilioGateway twilio;
    private final TwilioProperties twilioProps;
    private final MailGateway mail;
    private final SmtpProperties smtp;
    private final MessagingProperties costs;
    private final ObjectMapper json;

    public OutboundMessageService(
            OutboundMessageRepository messages,
            LegalCaseRepository cases,
            ClientRepository clients,
            ContactRepository contacts,
            FirmPhoneNumberRepository numbers,
            TenantRepository tenants,
            IntegrationService integrations,
            AuditService audit,
            TwilioGateway twilio,
            TwilioProperties twilioProps,
            MailGateway mail,
            SmtpProperties smtp,
            MessagingProperties costs,
            ObjectMapper json) {
        this.messages = messages;
        this.cases = cases;
        this.clients = clients;
        this.contacts = contacts;
        this.numbers = numbers;
        this.tenants = tenants;
        this.integrations = integrations;
        this.audit = audit;
        this.twilio = twilio;
        this.twilioProps = twilioProps;
        this.mail = mail;
        this.smtp = smtp;
        this.costs = costs;
        this.json = json;
    }

    public Map<String, Object> readiness() {
        requireStaff("sms");
        boolean connected = integrations.isConnected("twilio");
        boolean creds = twilioProps.configured();
        boolean smsFrom = smsSenderAvailable();
        boolean email = mail.configured();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("twilioConnected", connected);
        m.put("twilioConfigured", creds);
        m.put("smsReady", connected && creds && smsFrom);
        m.put("whatsappReady", connected && creds);
        m.put("emailReady", email);
        m.put("emailMode", email ? "smtp" : "dry_run");
        m.put("whatsappSandbox", twilioProps.whatsappSandbox());
        m.put("whatsappFrom", maskSender(twilioProps.whatsappSenderRaw()));
        m.put("attorneyNotice", ATTORNEY);
        m.put("smsHint", smsHint(connected, creds, smsFrom));
        m.put("whatsappHint", whatsappHint(connected, creds));
        m.put("emailHint", email
                ? "Email sends through the SMTP server configured on this API."
                : mail.unconfiguredReason());
        return m;
    }

    public List<Map<String, Object>> list(String caseId) {
        requireStaff("sms");
        UUID matter = uuid(caseId, "matter");
        List<OutboundMessage> rows = matter == null
                ? messages.findByTenantIdOrderByCreatedAtDesc(tid())
                : messages.findByTenantIdAndCaseIdOrderByCreatedAtDesc(tid(), matter);
        return rows.stream().limit(50).map(this::view).toList();
    }

    public Map<String, Object> sendSms(Map<String, Object> body) {
        return send("sms", body);
    }

    public Map<String, Object> sendWhatsapp(Map<String, Object> body) {
        return send("whatsapp", body);
    }

    public Map<String, Object> sendEmail(Map<String, Object> body) {
        return send("email", body);
    }

    public void onCarrierStatus(String token, Map<String, String[]> params, String signature) {
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            throw ApiException.forbidden("Twilio signature did not match.");
        }
        String url;
        try {
            url = twilioProps.callback("/api/v1/outbound/twilio/status/" + token);
        } catch (RuntimeException ex) {
            throw ApiException.forbidden("Twilio signature did not match.");
        }
        if (!TwilioSignature.valid(twilioProps.getAuthToken(), url, params, signature)) {
            throw ApiException.forbidden("Twilio signature did not match.");
        }
        OutboundMessage row = messages.findByCallbackToken(token).orElse(null);
        if (row == null) return;
        String mapped = mapCarrierStatus(first(params, "MessageStatus"));
        if (mapped != null) row.setStatus(mapped);
        String sid = first(params, "MessageSid");
        if (sid != null && !sid.isBlank() && (row.getProviderSid() == null || row.getProviderSid().isBlank())) {
            row.setProviderSid(clip(sid, 64));
        }
        String error = first(params, "ErrorMessage");
        if (error != null && !error.isBlank()) row.setErrorMessage(clip(safe(error), 500));
        messages.save(row);
        UUID previousTenant = TenantContext.getTenantId();
        UUID previousUser = TenantContext.getUserId();
        String previousRole = TenantContext.getRole();
        String previousEmail = TenantContext.getEmail();
        try {
            TenantContext.setTenantId(row.getTenantId());
            if (row.getActorId() != null) TenantContext.setUserId(row.getActorId());
            audit.record("outbound." + row.getChannel() + ".status", "outbound", row.getId().toString(),
                    row.getStatus());
        } finally {
            TenantContext.setTenantId(previousTenant);
            TenantContext.setUserId(previousUser);
            TenantContext.setRole(previousRole);
            TenantContext.setEmail(previousEmail);
        }
    }

    private Map<String, Object> send(String channel, Map<String, Object> body) {
        if (body == null) body = Map.of();
        requireStaff(channel);
        String rawBody = text(body, "body");
        if (rawBody.length() > EMAIL_MAX) {
            audit.record("outbound." + channel + ".failed", "outbound", null, "That message is too long.");
            throw ApiException.badRequest("That message is too long.");
        }
        OutboundMessage row = new OutboundMessage();
        row.setTenantId(tid());
        row.setActorId(TenantContext.getUserId());
        row.setChannel(channel);
        row.setStatus("queued");
        row.setProvider("email".equals(channel) ? "smtp" : "twilio");
        row.setBody(rawBody);
        row.setSubject(clip(text(body, "subject"), 200));
        row.setToAddress(clip(text(body, "to"), 320));
        row.setUnitCost(BigDecimal.ZERO);
        row.setCallbackToken(newToken());
        row.setCreatedAt(Instant.now());
        messages.save(row);
        try {
            if ("email".equals(channel)) return finishEmail(row, body);
            return finishPhone(row, channel, body);
        } catch (ApiException ex) {
            if ("queued".equals(row.getStatus())) markFailed(row, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            String message = safe(ex.getMessage());
            if ("queued".equals(row.getStatus())) markFailed(row, message);
            throw ApiException.badRequest(message);
        }
    }

    private Map<String, Object> finishPhone(OutboundMessage row, String channel, Map<String, Object> body) {
        String rawTo = text(body, "to");
        String textBody = text(body, "body");
        String contentSid = "whatsapp".equals(channel) ? text(body, "contentSid") : "";
        String contentVariables = "whatsapp".equals(channel) ? text(body, "contentVariables") : "";
        if (EmergencyNumbers.isEmergency(rawTo)) {
            return refuse(row, rawTo, EMERGENCY);
        }
        validatePhoneBody(channel, textBody, contentSid, contentVariables);
        Link link = link(body);
        if (rawTo.isBlank()) rawTo = phoneFor(link);
        if (rawTo.isBlank()) throw ApiException.badRequest("Enter a phone number.");
        if (EmergencyNumbers.isEmergency(rawTo)) return refuse(row, rawTo, EMERGENCY);
        Tenant tenant = tenant();
        String to = E164.normalize(rawTo, tenant.getCountry());
        if (EmergencyNumbers.isEmergency(to)) return refuse(row, to, EMERGENCY);

        if (!integrations.isConnected("twilio")) throw ApiException.badRequest(CONNECT_TWILIO);
        if (!twilioProps.configured()) throw ApiException.badRequest(MISSING_TWILIO);

        String messagingSid = "";
        String from;
        if ("whatsapp".equals(channel)) {
            from = E164.normalize(twilioProps.whatsappSenderRaw(), "US");
        } else {
            SmsSender sender = smsSender(tenant);
            from = sender.from;
            messagingSid = sender.messagingServiceSid;
        }

        row.setCaseId(link.caseId);
        row.setClientId(link.clientId);
        row.setContactId(link.contactId);
        row.setToAddress(to);
        row.setFromAddress(from == null ? "" : from);
        row.setBody(textBody.isBlank() ? "Template " + contentSid : textBody);
        row.setProvider("twilio");
        messages.save(row);

        String callback = statusCallback(row);
        String wireTo = "whatsapp".equals(channel) ? "whatsapp:" + to : to;
        String wireFrom = "whatsapp".equals(channel) ? "whatsapp:" + from : (from == null ? "" : from);
        String wireBody = contentSid.isBlank() ? textBody : "";
        String sid = twilio.sendMessage(new TwilioMessage(
                wireTo, wireFrom, messagingSid, wireBody, contentSid, contentVariables, callback));
        row.setStatus("sent");
        row.setProviderSid(sid);
        row.setUnitCost(unitCost(channel));
        row.setErrorMessage(null);
        messages.save(row);
        audit.record("outbound." + channel + ".sent", "outbound", row.getId().toString(), to);
        return view(row);
    }

    private Map<String, Object> finishEmail(OutboundMessage row, Map<String, Object> body) {
        String subject = text(body, "subject");
        String textBody = text(body, "body");
        if (subject.isBlank()) throw ApiException.badRequest("Enter a subject.");
        if (subject.length() > 200) throw ApiException.badRequest("That subject is too long.");
        if (subject.indexOf('\n') >= 0 || subject.indexOf('\r') >= 0) {
            throw ApiException.badRequest("The subject cannot contain a new line.");
        }
        if (textBody.isBlank()) throw ApiException.badRequest("Enter a message.");
        if (textBody.length() > EMAIL_MAX) throw ApiException.badRequest("That message is too long.");
        Link link = link(body);
        String rawTo = text(body, "to");
        if (rawTo.isBlank()) rawTo = emailFor(link);
        if (rawTo.isBlank()) throw ApiException.badRequest("Enter an email address.");
        if (rawTo.indexOf('\n') >= 0 || rawTo.indexOf('\r') >= 0 || !EMAIL.matcher(rawTo).matches()) {
            throw ApiException.badRequest("That email address is not valid.");
        }
        row.setCaseId(link.caseId);
        row.setClientId(link.clientId);
        row.setContactId(link.contactId);
        row.setToAddress(rawTo);
        row.setSubject(subject);
        row.setBody(textBody);
        row.setFromAddress(smtp.getFrom() == null ? "" : smtp.getFrom().trim());
        if (!mail.configured()) {
            row.setStatus("dry_run");
            row.setProvider("none");
            row.setUnitCost(BigDecimal.ZERO);
            row.setErrorMessage(mail.unconfiguredReason());
            messages.save(row);
            audit.record("outbound.email.dry_run", "outbound", row.getId().toString(), rawTo);
            String preview = textBody.length() > 400 ? textBody.substring(0, 400) + "..." : textBody;
            log.info("Email dry-run to={} subject={} body={}", rawTo, subject, preview);
            return view(row);
        }
        String from = smtp.getFrom().trim();
        if (from.indexOf('\n') >= 0 || from.indexOf('\r') >= 0) {
            throw ApiException.badRequest("SMTP_FROM is not a valid address.");
        }
        row.setFromAddress(from);
        messages.save(row);
        mail.send(from, rawTo, subject, textBody);
        row.setStatus("sent");
        row.setProvider("smtp");
        row.setUnitCost(BigDecimal.ZERO);
        row.setErrorMessage(null);
        messages.save(row);
        audit.record("outbound.email.sent", "outbound", row.getId().toString(), rawTo);
        return view(row);
    }

    private Map<String, Object> refuse(OutboundMessage row, String to, String message) {
        row.setToAddress(clip(to, 320));
        row.setStatus("refused");
        row.setUnitCost(BigDecimal.ZERO);
        row.setErrorMessage(message);
        messages.save(row);
        audit.record("outbound." + row.getChannel() + ".refused", "outbound", row.getId().toString(), message);
        throw ApiException.badRequest(message);
    }

    private void markFailed(OutboundMessage row, String message) {
        String clean = clip(safe(message), 500);
        if (clean.isBlank()) clean = "The message could not be sent.";
        row.setStatus("failed");
        row.setUnitCost(BigDecimal.ZERO);
        row.setErrorMessage(clean);
        messages.save(row);
        audit.record("outbound." + row.getChannel() + ".failed", "outbound",
                row.getId() == null ? null : row.getId().toString(), clean);
    }

    private void validatePhoneBody(String channel, String textBody, String contentSid, String contentVariables) {
        if ("whatsapp".equals(channel) && !contentSid.isBlank()) {
            if (!CONTENT_SID.matcher(contentSid).matches()) {
                throw ApiException.badRequest("That WhatsApp template id is not valid. It looks like HX followed by 32 hex characters.");
            }
            if (!contentVariables.isBlank()) {
                try {
                    JsonNode node = json.readTree(contentVariables);
                    if (node == null || !node.isObject()) {
                        throw ApiException.badRequest("Template variables must be a JSON object, for example {\"1\":\"Nomsa\"}.");
                    }
                } catch (ApiException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw ApiException.badRequest("Template variables must be a JSON object, for example {\"1\":\"Nomsa\"}.");
                }
                if (contentVariables.length() > 1000) {
                    throw ApiException.badRequest("Those template variables are too long.");
                }
            }
            if (textBody.length() > WHATSAPP_MAX) {
                throw ApiException.badRequest("That WhatsApp message is too long.");
            }
            return;
        }
        if (textBody.isBlank()) throw ApiException.badRequest("Enter a message.");
        int max = "whatsapp".equals(channel) ? WHATSAPP_MAX : SMS_MAX;
        if (textBody.length() > max) {
            throw ApiException.badRequest("whatsapp".equals(channel)
                    ? "That WhatsApp message is too long."
                    : "That SMS is too long. Keep it under 1600 characters.");
        }
    }

    private Link link(Map<String, Object> body) {
        UUID caseId = uuid(text(body, "caseId"), "matter");
        UUID clientId = uuid(text(body, "clientId"), "client");
        UUID contactId = uuid(text(body, "contactId"), "contact");
        if (caseId != null) {
            LegalCase matter = cases.findByIdAndTenantId(caseId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That matter is not on this firm."));
            if (clientId != null && matter.getClientId() != null && !clientId.equals(matter.getClientId())) {
                throw ApiException.badRequest("That client is not the client on this matter.");
            }
            if (clientId == null) clientId = matter.getClientId();
            caseId = matter.getId();
        } else if (clientId != null) {
            clients.findByIdAndTenantId(clientId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That client is not on this firm."));
        }
        if (contactId != null) {
            contacts.findByIdAndTenantId(contactId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That contact is not on this firm."));
        }
        return new Link(caseId, clientId, contactId);
    }

    private String phoneFor(Link link) {
        if (link.clientId != null) {
            Client client = clients.findByIdAndTenantId(link.clientId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That client is not on this firm."));
            if (client.getPhone() != null && !client.getPhone().isBlank()) return client.getPhone().trim();
        }
        if (link.contactId != null) {
            Contact contact = contacts.findByIdAndTenantId(link.contactId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That contact is not on this firm."));
            if (contact.getPhone() != null && !contact.getPhone().isBlank()) return contact.getPhone().trim();
        }
        return "";
    }

    private String emailFor(Link link) {
        if (link.clientId != null) {
            Client client = clients.findByIdAndTenantId(link.clientId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That client is not on this firm."));
            if (client.getEmail() != null && !client.getEmail().isBlank()) return client.getEmail().trim();
        }
        if (link.contactId != null) {
            Contact contact = contacts.findByIdAndTenantId(link.contactId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That contact is not on this firm."));
            if (contact.getEmail() != null && !contact.getEmail().isBlank()) return contact.getEmail().trim();
        }
        return "";
    }

    private SmsSender smsSender(Tenant tenant) {
        String configured = twilioProps.getSmsFrom() == null ? "" : twilioProps.getSmsFrom().trim();
        if (!configured.isBlank()) {
            return new SmsSender(E164.normalize(configured, tenant.getCountry()), "");
        }
        String mg = twilioProps.getMessagingServiceSid() == null ? "" : twilioProps.getMessagingServiceSid().trim();
        if (!mg.isBlank()) {
            if (!MG_SID.matcher(mg).matches()) {
                throw ApiException.badRequest("TWILIO_MESSAGING_SERVICE_SID is not a valid messaging service id.");
            }
            return new SmsSender(null, mg);
        }
        String did = activeDid();
        if (did != null) return new SmsSender(did, "");
        throw ApiException.badRequest(MISSING_SMS_FROM);
    }

    private boolean smsSenderAvailable() {
        if (twilioProps.getSmsFrom() != null && !twilioProps.getSmsFrom().isBlank()) return true;
        if (twilioProps.getMessagingServiceSid() != null && MG_SID.matcher(twilioProps.getMessagingServiceSid().trim()).matches()) {
            return true;
        }
        return activeDid() != null;
    }

    private String activeDid() {
        String fallback = null;
        for (FirmPhoneNumber row : numbers.findByTenantIdOrderByCreatedAtDesc(tid())) {
            if (!FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) continue;
            if (!FirmPhoneNumber.KIND_DID.equals(row.getKind())) continue;
            if (row.isDefaultOutbound()) return row.getE164();
            if (fallback == null) fallback = row.getE164();
        }
        return fallback;
    }

    private String statusCallback(OutboundMessage row) {
        if (!twilioProps.hasPublicBaseUrl()) return "";
        try {
            return twilioProps.callback("/api/v1/outbound/twilio/status/" + row.getCallbackToken());
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private BigDecimal unitCost(String channel) {
        BigDecimal raw = "whatsapp".equals(channel) ? costs.getWhatsappUnitCost() : costs.getSmsUnitCost();
        if (raw == null || raw.signum() < 0) return BigDecimal.ZERO;
        return raw;
    }

    private void requireStaff(String channel) {
        String role = TenantContext.getRole();
        if (role != null && "client".equalsIgnoreCase(role)) {
            audit.record("outbound." + channel + ".refused", "outbound", null, "portal");
            throw ApiException.forbidden("Client portal users cannot send firm SMS, WhatsApp, or email.");
        }
        tid();
    }

    private Tenant tenant() {
        return tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) return "The message could not be sent.";
        String out = message;
        out = redact(out, twilioProps.getAuthToken());
        out = redact(out, smtp.getPassword());
        return out;
    }

    private static String redact(String message, String secret) {
        if (secret == null || secret.isBlank() || message == null) return message;
        return message.replace(secret, "[redacted]");
    }

    private Map<String, Object> view(OutboundMessage row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("channel", row.getChannel());
        m.put("status", row.getStatus());
        m.put("to", row.getToAddress());
        m.put("from", row.getFromAddress());
        m.put("subject", row.getSubject());
        m.put("body", row.getBody());
        m.put("caseId", row.getCaseId());
        m.put("clientId", row.getClientId());
        m.put("contactId", row.getContactId());
        m.put("provider", row.getProvider());
        m.put("providerSid", row.getProviderSid());
        m.put("unitCost", row.getUnitCost());
        m.put("errorMessage", row.getErrorMessage());
        m.put("createdAt", row.getCreatedAt());
        if ("dry_run".equals(row.getStatus())) m.put("notice", row.getErrorMessage());
        if ("sent".equals(row.getStatus()) || "delivered".equals(row.getStatus())) {
            m.put("notice", "email".equals(row.getChannel()) ? "Email sent." : "Message sent.");
        }
        return m;
    }

    private String smsHint(boolean connected, boolean creds, boolean smsFrom) {
        if (!connected) return CONNECT_TWILIO;
        if (!creds) return MISSING_TWILIO;
        if (!smsFrom) return MISSING_SMS_FROM;
        return "SMS is pay-what-you-use and lands on the month-end usage invoice.";
    }

    private String whatsappHint(boolean connected, boolean creds) {
        if (!connected) return CONNECT_TWILIO;
        if (!creds) return MISSING_TWILIO;
        if (twilioProps.whatsappSandbox()) {
            return "WhatsApp sender is the Twilio sandbox +1 415 523 8886. The recipient must join that sandbox from their own WhatsApp before a message will arrive. Outside the 24 hour window, send an approved template.";
        }
        return "WhatsApp sends from the number in TWILIO_WHATSAPP_FROM. Outside the 24 hour window, send an approved template.";
    }

    private static String maskSender(String raw) {
        return raw == null ? "" : raw;
    }

    private static String mapCarrierStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.toLowerCase()) {
            case "queued", "accepted", "sending", "sent" -> "sent";
            case "delivered", "read" -> "delivered";
            case "failed", "undelivered" -> "failed";
            default -> null;
        };
    }

    private static String first(Map<String, String[]> params, String name) {
        if (params == null) return null;
        String[] values = params.get(name);
        if (values == null || values.length == 0) return null;
        return values[0];
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return "";
        return String.valueOf(body.get(key)).trim();
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static UUID uuid(String raw, String label) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("That " + label + " id is not valid.");
        }
    }

    private static String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private record Link(UUID caseId, UUID clientId, UUID contactId) {}

    private record SmsSender(String from, String messagingServiceSid) {}
}
