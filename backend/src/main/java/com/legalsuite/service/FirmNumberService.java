package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.FirmPhoneNumber;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.FirmPhoneNumberRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.voice.E164;
import com.legalsuite.voice.EmergencyNumbers;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FirmNumberService {
    private final FirmPhoneNumberRepository numbers;
    private final TenantRepository tenants;
    private final TwilioGateway twilio;
    private final TwilioProperties props;
    private final IntegrationService integrations;
    private final AuditService audit;

    public FirmNumberService(
            FirmPhoneNumberRepository numbers,
            TenantRepository tenants,
            TwilioGateway twilio,
            TwilioProperties props,
            IntegrationService integrations,
            AuditService audit) {
        this.numbers = numbers;
        this.tenants = tenants;
        this.twilio = twilio;
        this.props = props;
        this.integrations = integrations;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        requireStaff();
        return numbers.findByTenantIdOrderByCreatedAtDesc(tid()).stream()
                .filter(row -> !FirmPhoneNumber.STATUS_RELEASED.equals(row.getStatus()))
                .map(this::view)
                .toList();
    }

    public Map<String, Object> readiness() {
        requireStaff();
        boolean connected = integrations.isConnected("twilio");
        boolean credentials = props.configured();
        boolean publicBase = props.hasPublicBaseUrl();
        List<Map<String, Object>> callerIds = numbers.findByTenantIdOrderByCreatedAtDesc(tid()).stream()
                .filter(row -> !FirmPhoneNumber.STATUS_RELEASED.equals(row.getStatus()))
                .map(this::view)
                .toList();
        FirmPhoneNumber automatic = null;
        if (connected && credentials && publicBase) {
            try {
                automatic = resolveCallerId(null);
            } catch (ApiException ex) {
                automatic = null;
            }
        }
        String message;
        if (!connected) {
            message = "Connect Twilio on the Integrations page. The toggle does not store a password.";
        } else if (!credentials) {
            message = "Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN in the server environment. Do not paste them into the app.";
        } else if (!publicBase) {
            message = "Set TWILIO_PUBLIC_BASE_URL to the public https address of this API so Twilio can reach the bridge.";
        } else if (automatic != null) {
            message = "Ready. Your phone rings first. Automatic caller ID is " + automatic.getE164()
                    + ". Buying a number is optional.";
        } else {
            message = NO_CALLER_ID;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("twilioConnected", connected);
        out.put("credentialsPresent", credentials);
        out.put("publicBaseUrlSet", publicBase);
        out.put("callerIds", callerIds);
        out.put("canDial", automatic != null);
        out.put("automaticCallerId", automatic == null ? null : automatic.getE164());
        out.put("automaticSource", automatic == null ? null : automatic.getKind());
        out.put("message", message);
        return out;
    }

    public List<Map<String, String>> search(Map<String, Object> body) {
        requireReady(false);
        String country = text(body, "country");
        if (country.isBlank()) country = tenant().getCountry();
        String area = digits(text(body, "areaCode"), 6);
        String contains = digits(text(body, "contains"), 12);
        String locality = place(text(body, "locality"));
        return twilio.searchLocal(country, area, contains, locality);
    }

    public Map<String, Object> buy(Map<String, Object> body) {
        requireStaff();
        String raw = text(body, "phoneNumber");
        EmergencyNumbers.rejectIfEmergency(raw);
        requireReady(true);
        Tenant tenant = tenant();
        String e164 = E164.normalize(raw, tenant.getCountry());
        EmergencyNumbers.rejectIfEmergency(e164);
        ensureFree(e164);
        String friendly = label(text(body, "friendlyName"), tenant.getFirmName() + " line");
        Map<String, String> bought = twilio.buyLocal(
                e164,
                friendly,
                props.callback("/api/v1/voice/twilio/inbound"),
                props.callback("/api/v1/voice/twilio/inbound-status"));
        String canonical = bought.getOrDefault("phoneNumber", "");
        if (canonical.isBlank()) canonical = e164;
        FirmPhoneNumber row = new FirmPhoneNumber();
        row.setTenantId(tid());
        row.setE164(canonical);
        row.setKind(FirmPhoneNumber.KIND_DID);
        row.setStatus(FirmPhoneNumber.STATUS_ACTIVE);
        row.setTwilioSid(blankToNull(bought.get("sid")));
        row.setFriendlyName(friendly);
        row.setLocality(place(text(body, "locality")));
        row.setRegion(place(text(body, "region")));
        row.setCountry(countryOf(canonical, tenant.getCountry()));
        row.setVerifiedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        promoteDefaultIfNone(row);
        numbers.save(row);
        audit.record("voice.number.buy", "phone", row.getId().toString(), row.getE164());
        return view(row);
    }

    public Map<String, Object> startVerification(Map<String, Object> body) {
        requireStaff();
        String raw = text(body, "phoneNumber");
        EmergencyNumbers.rejectIfEmergency(raw);
        requireReady(true);
        Tenant tenant = tenant();
        String e164 = E164.normalize(raw, tenant.getCountry());
        EmergencyNumbers.rejectIfEmergency(e164);
        ensureFree(e164);
        String friendly = label(text(body, "friendlyName"), tenant.getFirmName() + " personal");
        Map<String, String> started = twilio.startCallerIdVerification(
                e164,
                friendly,
                props.callback("/api/v1/voice/twilio/verification"));
        String canonical = started.getOrDefault("phoneNumber", "");
        if (canonical.isBlank()) canonical = e164;
        String code = started.getOrDefault("validationCode", "");
        if (code.isBlank()) {
            throw ApiException.badRequest("Twilio did not return a verification code. Try that phone again.");
        }
        FirmPhoneNumber row = new FirmPhoneNumber();
        row.setTenantId(tid());
        row.setE164(canonical);
        row.setKind(FirmPhoneNumber.KIND_LANDLINE);
        row.setStatus(FirmPhoneNumber.STATUS_PENDING);
        row.setValidationCode(code);
        row.setFriendlyName(friendly);
        row.setCountry(countryOf(canonical, tenant.getCountry()));
        row.setUpdatedAt(Instant.now());
        numbers.save(row);
        audit.record("voice.number.verify", "phone", row.getId().toString(), "pending");
        return view(row);
    }

    public Map<String, Object> refresh(UUID id) {
        requireReady(false);
        FirmPhoneNumber row = requireOwn(id);
        if (!FirmPhoneNumber.KIND_LANDLINE.equals(row.getKind())) {
            return view(row);
        }
        if (FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) {
            return view(row);
        }
        String sid = twilio.findVerifiedCallerIdSid(row.getE164());
        if (sid == null) return view(row);
        activate(row, sid);
        return view(row);
    }

    public void markVerifiedFromCarrier(String rawNumber, String verificationStatus, String sid) {
        if (rawNumber == null || rawNumber.isBlank()) return;
        String e164;
        try {
            e164 = rawNumber.trim().startsWith("+") ? rawNumber.trim() : E164.normalize(rawNumber, "US");
        } catch (ApiException ex) {
            return;
        }
        List<FirmPhoneNumber> rows = numbers.findByE164AndStatusIn(
                e164, List.of(FirmPhoneNumber.STATUS_PENDING, FirmPhoneNumber.STATUS_ACTIVE));
        for (FirmPhoneNumber row : rows) {
            if (!FirmPhoneNumber.KIND_LANDLINE.equals(row.getKind())) continue;
            TenantContext.setTenantId(row.getTenantId());
            if ("success".equalsIgnoreCase(verificationStatus)) {
                if (!FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) activate(row, sid);
            } else if ("failed".equalsIgnoreCase(verificationStatus)
                    && FirmPhoneNumber.STATUS_PENDING.equals(row.getStatus())) {
                row.setStatus("failed");
                row.setValidationCode(null);
                row.setUpdatedAt(Instant.now());
                numbers.save(row);
                audit.record("voice.number.verify", "phone", row.getId().toString(), "failed");
            }
        }
    }

    public Map<String, Object> makeDefault(UUID id) {
        requireStaff();
        FirmPhoneNumber row = requireOwn(id);
        if (!FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) {
            throw ApiException.badRequest("Only an active caller ID can be the default.");
        }
        for (FirmPhoneNumber other : numbers.findByTenantIdOrderByCreatedAtDesc(tid())) {
            if (other.isDefaultOutbound() && !other.getId().equals(row.getId())) {
                other.setDefaultOutbound(false);
                other.setUpdatedAt(Instant.now());
                numbers.save(other);
            }
        }
        row.setDefaultOutbound(true);
        row.setUpdatedAt(Instant.now());
        numbers.save(row);
        audit.record("voice.number.default", "phone", row.getId().toString(), row.getE164());
        return view(row);
    }

    public Map<String, Object> release(UUID id) {
        requireStaff();
        FirmPhoneNumber row = requireOwn(id);
        if (FirmPhoneNumber.STATUS_RELEASED.equals(row.getStatus())) return view(row);
        if (row.getTwilioSid() != null && !row.getTwilioSid().isBlank()) {
            requireReady(false);
            if (FirmPhoneNumber.KIND_DID.equals(row.getKind())) twilio.releaseIncoming(row.getTwilioSid());
            else twilio.releaseCallerId(row.getTwilioSid());
        }
        row.setStatus(FirmPhoneNumber.STATUS_RELEASED);
        row.setDefaultOutbound(false);
        row.setValidationCode(null);
        row.setUpdatedAt(Instant.now());
        numbers.save(row);
        audit.record("voice.number.release", "phone", row.getId().toString(), row.getE164());
        return view(row);
    }

    public FirmPhoneNumber requireForDial(UUID id) {
        requireReady(true);
        return resolveCallerId(id);
    }

    /**
     * Caller ID for an outbound bridge.
     * Rented DID, then a verified personal number saved on the firm, then TWILIO_VOICE_FROM,
     * then the first number already on the Twilio account. No purchase and no regulatory bundle.
     */
    FirmPhoneNumber resolveCallerId(UUID id) {
        if (id != null) {
            FirmPhoneNumber row = numbers.findByIdAndTenantId(id, tid())
                    .orElseThrow(() -> ApiException.notFound("That caller ID is not on this firm."));
            if (!FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) {
                throw ApiException.badRequest(
                        "That caller ID is not verified yet. Answer Twilio's call and enter the code. A mobile phone works the same way as a landline.");
            }
            if (!FirmPhoneNumber.KIND_DID.equals(row.getKind()) && !FirmPhoneNumber.KIND_LANDLINE.equals(row.getKind())) {
                throw ApiException.badRequest("That caller ID cannot be used for outbound calls.");
            }
            return row;
        }
        List<FirmPhoneNumber> active = numbers.findByTenantIdOrderByCreatedAtDesc(tid()).stream()
                .filter(row -> FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus()))
                .toList();
        FirmPhoneNumber did = prefer(active, FirmPhoneNumber.KIND_DID);
        if (did != null) return did;
        FirmPhoneNumber personal = prefer(active, FirmPhoneNumber.KIND_LANDLINE);
        if (personal != null) return personal;
        if (props.hasVoiceFrom()) {
            String from = E164.normalize(props.getVoiceFrom(), tenant().getCountry());
            EmergencyNumbers.rejectIfEmergency(from);
            return external(from, FirmPhoneNumber.KIND_VOICE_FROM);
        }
        String incoming = twilio.firstIncomingNumber();
        if (incoming != null && !incoming.isBlank()) {
            return external(incoming, FirmPhoneNumber.KIND_ACCOUNT_INCOMING);
        }
        String outgoing = twilio.firstOutgoingCallerId();
        if (outgoing != null && !outgoing.isBlank()) {
            return external(outgoing, FirmPhoneNumber.KIND_ACCOUNT_OUTGOING);
        }
        throw ApiException.badRequest(NO_CALLER_ID);
    }

    static final String NO_CALLER_ID =
            "No caller ID is available. Verify a personal mobile or landline in the app, or set TWILIO_VOICE_FROM to a number this Twilio account already owns or has verified.";

    private FirmPhoneNumber prefer(List<FirmPhoneNumber> active, String kind) {
        FirmPhoneNumber fallback = null;
        for (FirmPhoneNumber row : active) {
            if (!kind.equals(row.getKind())) continue;
            if (row.isDefaultOutbound()) return row;
            if (fallback == null) fallback = row;
        }
        return fallback;
    }

    private FirmPhoneNumber external(String e164, String kind) {
        FirmPhoneNumber row = new FirmPhoneNumber();
        row.setTenantId(tid());
        row.setE164(e164);
        row.setKind(kind);
        row.setStatus(FirmPhoneNumber.STATUS_ACTIVE);
        return row;
    }

    public List<FirmPhoneNumber> findActiveDid(String e164) {
        List<FirmPhoneNumber> out = new ArrayList<>();
        for (FirmPhoneNumber row : numbers.findByE164AndStatusIn(e164, List.of(FirmPhoneNumber.STATUS_ACTIVE))) {
            if (FirmPhoneNumber.KIND_DID.equals(row.getKind())) out.add(row);
        }
        return out;
    }

    private void activate(FirmPhoneNumber row, String sid) {
        row.setStatus(FirmPhoneNumber.STATUS_ACTIVE);
        row.setVerifiedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        row.setValidationCode(null);
        if (sid != null && !sid.isBlank()) row.setTwilioSid(sid);
        promoteDefaultIfNone(row);
        numbers.save(row);
        audit.record("voice.number.verify", "phone", row.getId().toString(), "active");
    }

    private void promoteDefaultIfNone(FirmPhoneNumber row) {
        if (!FirmPhoneNumber.STATUS_ACTIVE.equals(row.getStatus())) return;
        boolean exists = numbers.findFirstByTenantIdAndDefaultOutboundTrueAndStatus(
                row.getTenantId(), FirmPhoneNumber.STATUS_ACTIVE).isPresent();
        if (!exists) row.setDefaultOutbound(true);
    }

    private void ensureFree(String e164) {
        for (FirmPhoneNumber row : numbers.findByE164AndStatusIn(
                e164, List.of(FirmPhoneNumber.STATUS_PENDING, FirmPhoneNumber.STATUS_ACTIVE))) {
            if (row.getTenantId().equals(tid())) {
                throw ApiException.conflict("This firm already has that number.");
            }
            throw ApiException.conflict("That number is already in use.");
        }
    }

    private FirmPhoneNumber requireOwn(UUID id) {
        return numbers.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("That caller ID is not on this firm."));
    }

    public void requireReady(boolean needPublicUrl) {
        requireStaff();
        if (!integrations.isConnected("twilio")) {
            throw ApiException.badRequest(
                    "Connect Twilio on the Integrations page before using public network calling. The toggle does not store a password.");
        }
        if (!props.configured()) {
            throw ApiException.badRequest(
                    "Twilio is connected for this firm, but the server has no TWILIO_ACCOUNT_SID or TWILIO_AUTH_TOKEN. Put those in the environment. Do not paste them into the app.");
        }
        if (needPublicUrl && !props.hasPublicBaseUrl()) {
            throw ApiException.badRequest(
                    "Set TWILIO_PUBLIC_BASE_URL to the public https address of this API so Twilio can reach the bridge.");
        }
    }

    private void requireStaff() {
        String role = TenantContext.getRole();
        if (role != null && "client".equalsIgnoreCase(role)) {
            throw ApiException.forbidden("Client portal users cannot place firm calls or change caller IDs.");
        }
    }

    private Map<String, Object> view(FirmPhoneNumber row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("e164", row.getE164());
        m.put("kind", row.getKind());
        m.put("status", row.getStatus());
        m.put("locality", row.getLocality());
        m.put("region", row.getRegion());
        m.put("country", row.getCountry());
        m.put("friendlyName", row.getFriendlyName());
        m.put("defaultOutbound", row.isDefaultOutbound());
        m.put("verifiedAt", row.getVerifiedAt());
        if (FirmPhoneNumber.STATUS_PENDING.equals(row.getStatus())) {
            m.put("validationCode", row.getValidationCode());
        }
        return m;
    }

    private Tenant tenant() {
        return tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return "";
        return String.valueOf(body.get(key)).trim();
    }

    private static String digits(String raw, int max) {
        if (raw == null || raw.isBlank()) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() > max) {
            throw ApiException.badRequest("That search filter is too long.");
        }
        return digits;
    }

    private static String place(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.length() > 64) cleaned = cleaned.substring(0, 64);
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String label(String raw, String fallback) {
        String name = raw == null || raw.isBlank() ? fallback : raw;
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) name = "Firm line";
        if (name.length() > 64) name = name.substring(0, 64);
        return name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String countryOf(String e164, String fallback) {
        if (e164 != null && e164.startsWith("+27")) return "ZA";
        if (e164 != null && e164.startsWith("+1")) return "US";
        if (e164 != null && e164.startsWith("+44")) return "GB";
        if (fallback == null || fallback.isBlank()) return null;
        String c = fallback.trim().toUpperCase(Locale.ROOT);
        return c.length() >= 2 ? c.substring(0, 2) : c;
    }
}
