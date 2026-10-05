package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.FirmPhoneNumber;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.Tenant;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.repo.TimeEntryRepository;
import com.legalsuite.voice.E164;
import com.legalsuite.voice.EmergencyNumbers;
import com.legalsuite.voice.PstnBridge;
import com.legalsuite.voice.TwilioGateway;
import com.legalsuite.voice.TwilioProperties;
import com.legalsuite.voice.TwilioSignature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceService {
    private final CallRecordRepository calls;
    private final TimeEntryRepository timeEntries;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final NoteRepository notes;
    private final FirmNumberService firmNumbers;
    private final LegalCaseRepository cases;
    private final TwilioGateway twilio;
    private final TwilioProperties twilioProps;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final BigDecimal outboundPerMinute;
    private final BigDecimal inboundPerMinute;
    private final Map<String, List<Map<String, Object>>> inbox = new ConcurrentHashMap<>();

    public VoiceService(
            CallRecordRepository calls,
            TimeEntryRepository timeEntries,
            AppUserRepository users,
            TenantRepository tenants,
            NoteRepository notes,
            FirmNumberService firmNumbers,
            LegalCaseRepository cases,
            TwilioGateway twilio,
            TwilioProperties twilioProps,
            AuditService audit,
            @Value("${legalsuite.pstn.outbound-per-minute:}") String outboundPerMinute,
            @Value("${legalsuite.pstn.inbound-per-minute:}") String inboundPerMinute) {
        this.calls = calls;
        this.timeEntries = timeEntries;
        this.users = users;
        this.tenants = tenants;
        this.notes = notes;
        this.firmNumbers = firmNumbers;
        this.cases = cases;
        this.twilio = twilio;
        this.twilioProps = twilioProps;
        this.audit = audit;
        this.outboundPerMinute = rateOrZero(outboundPerMinute);
        this.inboundPerMinute = rateOrZero(inboundPerMinute);
    }

    public List<Map<String, Object>> history() {
        return calls.findByTenantIdOrderByStartedAtDesc(tid()).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> initiate(Map<String, Object> body) {
        CallRecord rec = new CallRecord();
        rec.setTenantId(tid());
        rec.setCallerUserId(TenantContext.requireUser());
        rec.setCallType(String.valueOf(body.getOrDefault("callType", "webrtc")));
        rec.setDirection(String.valueOf(body.getOrDefault("direction", "internal")));
        rec.setStatus("ringing");
        rec.setRecordingEnabled(Boolean.parseBoolean(String.valueOf(body.getOrDefault("recordingEnabled", "false"))));
        rec.setRecordingConsentGiven(rec.isRecordingEnabled()
                && Boolean.parseBoolean(String.valueOf(body.getOrDefault("recordingConsentGiven", rec.isRecordingEnabled() ? "true" : "false"))));
        if (rec.isRecordingEnabled() && !rec.isRecordingConsentGiven()) {
            throw ApiException.badRequest("Recording requires an explicit consent click. This product does not silently capture calls.");
        }
        if (body.get("calleeUserId") != null) rec.setCalleeUserId(UUID.fromString(String.valueOf(body.get("calleeUserId"))));
        if (body.get("caseId") != null) rec.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        if (body.get("clientId") != null) rec.setClientId(UUID.fromString(String.valueOf(body.get("clientId"))));
        rec.setCostPerMinute(costFor(rec.getCallType()));
        calls.save(rec);
        return view(rec);
    }

    @Transactional
    public Map<String, Object> answer(UUID id) {
        CallRecord rec = require(id);
        rec.setStatus("answered");
        rec.setAnsweredAt(Instant.now());
        calls.save(rec);
        return view(rec);
    }

    @Transactional
    public Map<String, Object> end(UUID id, Map<String, Object> body) {
        CallRecord rec = require(id);
        rec.setStatus("completed");
        rec.setEndedAt(Instant.now());
        Instant start = rec.getAnsweredAt() == null ? rec.getStartedAt() : rec.getAnsweredAt();
        int seconds = (int) Math.max(1, rec.getEndedAt().getEpochSecond() - start.getEpochSecond());
        rec.setDurationSeconds(seconds);
        BigDecimal minutes = BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        rec.setTotalCost(rec.getCostPerMinute().multiply(minutes).setScale(4, RoundingMode.HALF_UP));
        if (body != null && body.get("notes") != null) rec.setNotes(String.valueOf(body.get("notes")));
        if (body != null && body.get("transcript") != null) rec.setTranscript(String.valueOf(body.get("transcript")));
        if (body != null && body.get("recordingUrl") != null) rec.setRecordingUrl(String.valueOf(body.get("recordingUrl")));

        if (rec.getCaseId() != null && (rec.getNotes() != null || rec.getTranscript() != null)) {
            Note n = new Note();
            n.setTenantId(tid());
            n.setCaseId(rec.getCaseId());
            n.setUserId(rec.getCallerUserId());
            n.setTitle(rec.isRecordingEnabled() ? "Call recording (opt-in)" : "Call on the matter");
            StringBuilder bodyText = new StringBuilder();
            bodyText.append("Duration ").append(seconds).append("s. In-app calls are not billed. Public-network minutes are pay-what-you-use.");
            if (rec.isRecordingEnabled()) {
                bodyText.append(" Recording: opt-in, consent logged.");
            }
            if (rec.getNotes() != null) bodyText.append("\n").append(rec.getNotes());
            if (rec.getTranscript() != null) bodyText.append("\n\nTranscript (stays on this tenant):\n").append(rec.getTranscript());
            n.setBody(bodyText.toString());
            n.setType("call");
            notes.save(n);
        }

        if (rec.isBillable() && rec.getCallerUserId() != null) {
            AppUser user = users.findById(rec.getCallerUserId()).orElse(null);
            if (user != null) {
                int billedMinutes = Math.max(6, (int) Math.ceil(seconds / 60.0));
                billedMinutes = ((billedMinutes + 5) / 6) * 6;
                TimeEntry t = new TimeEntry();
                t.setTenantId(tid());
                t.setUserId(user.getId());
                t.setCaseId(rec.getCaseId());
                t.setDate(LocalDate.now());
                t.setDurationMinutes(billedMinutes);
                t.setHourlyRate(user.getHourlyRate() == null ? new BigDecimal("350") : user.getHourlyRate());
                t.setTotalAmount(t.getHourlyRate().multiply(BigDecimal.valueOf(billedMinutes))
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                t.setDescription("Telephone conference");
                t.setActivityType("call");
                t.setSource("voice_call");
                t.setCallRecordId(rec.getId());
                timeEntries.save(t);
                rec.setTimeEntryId(t.getId());
            }
        }
        calls.save(rec);
        return view(rec);
    }

    public Map<String, Object> registry() {
        List<CallRecord> all = calls.findByTenantIdOrderByStartedAtDesc(tid());
        int webrtc = 0;
        int pstn = 0;
        int pstnSeconds = 0;
        int seconds = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (CallRecord c : all) {
            seconds += c.getDurationSeconds();
            cost = cost.add(c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost());
            if ("webrtc".equals(c.getCallType())) webrtc++;
            else {
                pstn++;
                pstnSeconds += Math.max(0, c.getDurationSeconds());
            }
        }
        int pstnMinutes = Math.max(0, pstnSeconds) / 60;
        boolean rateSet = outboundPerMinute.signum() > 0 || inboundPerMinute.signum() > 0;
        String note = rateSet
                ? "Public-network minutes are pay-what-you-use at the rate configured on this process. No minute bundle is included. In-app calls are not billed."
                : "Public-network minutes are pay-what-you-use. No minute bundle is included. The per-minute rate is not set, so new public-network calls record duration at zero cost until LEGALSUITE_PSTN_OUTBOUND_PER_MIN and LEGALSUITE_PSTN_INBOUND_PER_MIN are set from the carrier price. In-app calls are not billed.";
        Map<String, Object> m = new HashMap<>();
        m.put("totalCalls", all.size());
        m.put("webrtcCalls", webrtc);
        m.put("pstnCalls", pstn);
        m.put("totalMinutes", Math.round(seconds / 60.0));
        m.put("totalCost", cost);
        m.put("includedMinutes", Pricing.INCLUDED_PSTN_MINUTES);
        m.put("billablePstnMinutes", pstnMinutes);
        m.put("rateConfigured", rateSet);
        m.put("didMonthly", Pricing.DID_MONTHLY_ZAR);
        m.put("note", note);
        return m;
    }

    public Map<String, Object> ethics() {
        Tenant tenant = tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
        Map<String, Object> rules = new HashMap<>(CallEthics.forTenant(tenant));
        rules.put("recordingOptIn", true);
        rules.put("inAppFree", true);
        rules.put("pstnNotice", CallEthics.pstnNotice());
        return rules;
    }

    /**
     * Callback bridge: Twilio rings the attorney, then dials the destination with the firm caller ID.
     * This is a real PSTN call, not a browser softphone.
     */
    public Map<String, Object> placePstn(Map<String, Object> body) {
        if (body == null) body = Map.of();
        String toRaw = String.valueOf(body.getOrDefault("to", ""));
        String callbackRaw = String.valueOf(body.getOrDefault("staffCallback", ""));
        EmergencyNumbers.rejectIfEmergency(toRaw);
        EmergencyNumbers.rejectIfEmergency(callbackRaw);
        boolean nonMatter = truthy(body.get("nonMatter"));
        UUID caseId = uuidOrNull(body.get("caseId"));
        if (caseId == null && !nonMatter) {
            throw ApiException.badRequest("Choose a matter, or mark the call as not on a matter, before you dial.");
        }
        if (caseId != null && nonMatter) {
            throw ApiException.badRequest("A call is either on a matter or explicitly not on a matter.");
        }
        boolean record = truthy(body.get("recordingEnabled"));
        boolean consent = truthy(body.get("recordingConsentGiven"));
        if (record && !consent) {
            throw ApiException.badRequest("Recording requires an explicit consent click. This product does not silently capture calls.");
        }
        FirmPhoneNumber line = firmNumbers.requireForDial(uuidOrNull(body.get("firmPhoneNumberId")));
        Tenant tenant = tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
        String to = E164.normalize(toRaw, tenant.getCountry());
        String callback = E164.normalize(callbackRaw, tenant.getCountry());
        EmergencyNumbers.rejectIfEmergency(to);
        EmergencyNumbers.rejectIfEmergency(callback);
        if (to.equals(callback)) {
            throw ApiException.badRequest(
                    "Your phone and the number you are calling must be different. Answer on your mobile so the other party sees the firm caller ID.");
        }
        if (to.equals(line.getE164()) || callback.equals(line.getE164())) {
            throw ApiException.badRequest(
                    "Answer on a different phone than the caller ID. The firm number is what the other party sees. Your mobile is what rings first.");
        }

        CallRecord rec = new CallRecord();
        rec.setTenantId(tid());
        rec.setCallerUserId(TenantContext.requireUser());
        rec.setCallType("pstn_outbound");
        rec.setDirection("outbound");
        rec.setStatus("ringing");
        rec.setFromNumber(line.getE164());
        rec.setToNumber(to);
        rec.setStaffCallbackNumber(callback);
        rec.setCallerIdKind(line.getKind());
        rec.setFirmPhoneNumberId(line.getId());
        rec.setRecordingEnabled(record);
        rec.setRecordingConsentGiven(record && consent);
        rec.setCostPerMinute(costFor("pstn_outbound"));
        rec.setBridgeToken(newToken());
        rec.setNonMatter(nonMatter);
        rec.setBillable(!nonMatter);
        rec.setNotes("Callback bridge. Caller ID " + line.getE164() + ".");
        if (caseId != null) {
            LegalCase matter = cases.findByIdAndTenantId(caseId, tid())
                    .orElseThrow(() -> ApiException.badRequest("That matter is not on this firm."));
            rec.setCaseId(matter.getId());
            rec.setClientId(matter.getClientId());
        }
        calls.save(rec);

        String bridge = twilioProps.callback("/api/v1/voice/twilio/bridge/" + rec.getBridgeToken());
        String status = twilioProps.callback("/api/v1/voice/twilio/status/" + rec.getBridgeToken());
        try {
            String sid = twilio.createCall(callback, line.getE164(), bridge, status);
            rec.setTwilioCallSid(sid);
            calls.save(rec);
        } catch (RuntimeException ex) {
            rec.setStatus("failed");
            rec.setEndedAt(Instant.now());
            rec.setTotalCost(BigDecimal.ZERO);
            calls.save(rec);
            throw ex;
        }
        audit.record("voice.pstn.dial", "call", rec.getId().toString(), line.getKind());
        return view(rec);
    }

    public void assertTwilioSignature(String path, Map<String, String[]> params, String signature) {
        if (!TwilioSignature.valid(twilioProps.getAuthToken(), twilioProps.callback(path), params, signature)) {
            throw ApiException.forbidden("Twilio signature did not match.");
        }
    }

    @Transactional
    public String bridgeTwiml(String token, String callSid) {
        CallRecord rec = requireBridge(token);
        if (callSid != null && !callSid.isBlank()) rec.setTwilioCallSid(callSid);
        if (!"completed".equals(rec.getStatus()) && !"failed".equals(rec.getStatus())) {
            rec.setStatus("answered");
            if (rec.getAnsweredAt() == null) rec.setAnsweredAt(Instant.now());
        }
        calls.save(rec);
        boolean record = rec.isRecordingEnabled() && rec.isRecordingConsentGiven();
        String action = twilioProps.callback("/api/v1/voice/twilio/dial-result/" + rec.getBridgeToken());
        String recordingCallback = record
                ? twilioProps.callback("/api/v1/voice/twilio/recording/" + rec.getBridgeToken())
                : null;
        return PstnBridge.twiml(rec.getFromNumber(), rec.getToNumber(), action, record, recordingCallback);
    }

    @Transactional
    public void onDialResult(String token, String dialStatus, String dialDuration, String recordingUrl) {
        CallRecord rec = requireBridge(token);
        bindTenant(rec);
        if (rec.getEndedAt() != null) {
            attachRecording(rec, recordingUrl);
            return;
        }
        String status = dialStatus == null ? "" : dialStatus.toLowerCase(Locale.ROOT);
        if ("completed".equals(status) || "answered".equals(status)) {
            closeCompleted(rec, parseSeconds(dialDuration), recordingUrl);
        } else {
            closeUnsuccessful(rec, mapCarrierStatus(status));
        }
    }

    @Transactional
    public void onParentStatus(String token, String callStatus, String callDuration, String recordingUrl) {
        CallRecord rec = requireBridge(token);
        bindTenant(rec);
        if (rec.getEndedAt() != null) {
            attachRecording(rec, recordingUrl);
            return;
        }
        String status = callStatus == null ? "" : callStatus.toLowerCase(Locale.ROOT);
        switch (status) {
            case "queued", "initiated", "ringing" -> {
                rec.setStatus("ringing");
                calls.save(rec);
            }
            case "in-progress", "answered" -> {
                rec.setStatus("answered");
                if (rec.getAnsweredAt() == null) rec.setAnsweredAt(Instant.now());
                calls.save(rec);
            }
            case "completed" -> closeCompleted(rec, parseSeconds(callDuration), recordingUrl);
            case "busy", "no-answer", "noanswer", "failed", "canceled", "cancelled" ->
                    closeUnsuccessful(rec, mapCarrierStatus(status));
            default -> {
                /* ignore unknown interim events */
            }
        }
    }

    @Transactional
    public void onRecording(String token, String recordingUrl, String recordingStatus) {
        CallRecord rec = requireBridge(token);
        if (!rec.isRecordingEnabled() || !rec.isRecordingConsentGiven()) return;
        if (recordingStatus != null && !recordingStatus.isBlank() && !"completed".equalsIgnoreCase(recordingStatus)) return;
        attachRecording(rec, recordingUrl);
    }

    @Transactional
    public String inboundTwiml(String from, String to, String callSid) {
        String toE164 = canonicalOrNull(to);
        if (toE164 == null) return PstnBridge.say("This number is not in service.");
        List<FirmPhoneNumber> lines = firmNumbers.findActiveDid(toE164);
        if (lines.isEmpty()) return PstnBridge.say("This number is not in service.");
        FirmPhoneNumber line = lines.get(0);
        Tenant tenant = tenants.findById(line.getTenantId()).orElse(null);
        CallRecord rec = new CallRecord();
        rec.setTenantId(line.getTenantId());
        rec.setCallType("pstn_inbound");
        rec.setDirection("inbound");
        rec.setStatus("ringing");
        rec.setFromNumber(from);
        rec.setToNumber(line.getE164());
        rec.setCallerIdKind(line.getKind());
        rec.setFirmPhoneNumberId(line.getId());
        rec.setTwilioCallSid(callSid);
        rec.setCostPerMinute(costFor("pstn_inbound"));
        rec.setBillable(false);
        rec.setNotes("Inbound call to the firm number.");
        calls.save(rec);
        String firm = tenant == null || tenant.getFirmName() == null ? "the firm" : tenant.getFirmName();
        return PstnBridge.say("You have reached " + firm + ". This line does not take a message. Please use the number published on the firm website.");
    }

    @Transactional
    public void onInboundStatus(String callSid, String callStatus, String callDuration) {
        if (callSid == null || callSid.isBlank()) return;
        CallRecord rec = calls.findByTwilioCallSid(callSid).orElse(null);
        if (rec == null || rec.getEndedAt() != null) return;
        bindTenant(rec);
        String status = callStatus == null ? "" : callStatus.toLowerCase(Locale.ROOT);
        if ("completed".equals(status)) {
            closeCompleted(rec, parseSeconds(callDuration), null);
        } else if ("busy".equals(status) || "no-answer".equals(status) || "failed".equals(status) || "canceled".equals(status)) {
            closeUnsuccessful(rec, mapCarrierStatus(status));
        }
    }

    public Map<String, Object> iceServers() {
        return Map.of("iceServers", List.of(
                Map.of("urls", "stun:stun.l.google.com:19302"),
                Map.of("urls", "stun:stun1.l.google.com:19302")
        ));
    }

    public void pushSignal(Map<String, Object> body) {
        Object to = body.get("to");
        if (to == null) return;
        Map<String, Object> copy = new HashMap<>(body);
        copy.put("from", TenantContext.requireUser().toString());
        inbox.computeIfAbsent(String.valueOf(to), k -> new CopyOnWriteArrayList<>()).add(copy);
    }

    public List<Map<String, Object>> drainInbox() {
        List<Map<String, Object>> queued = inbox.remove(TenantContext.requireUser().toString());
        return queued == null ? List.of() : new ArrayList<>(queued);
    }

    static BigDecimal rateOrZero(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            return parsed.signum() < 0 ? BigDecimal.ZERO : parsed;
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal costFor(String type) {
        return switch (type) {
            case "pstn_outbound" -> outboundPerMinute;
            case "pstn_inbound" -> inboundPerMinute;
            default -> BigDecimal.ZERO;
        };
    }

    private CallRecord require(UUID id) {
        return calls.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Call not found"));
    }

    private Map<String, Object> view(CallRecord c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("callType", c.getCallType());
        m.put("direction", c.getDirection());
        m.put("status", c.getStatus());
        m.put("startedAt", c.getStartedAt());
        m.put("endedAt", c.getEndedAt());
        m.put("durationSeconds", c.getDurationSeconds());
        m.put("totalCost", c.getTotalCost());
        m.put("recordingEnabled", c.isRecordingEnabled());
        m.put("recordingConsentGiven", c.isRecordingConsentGiven());
        m.put("recordingUrl", c.getRecordingUrl());
        m.put("transcript", c.getTranscript());
        m.put("callerUserId", c.getCallerUserId());
        m.put("calleeUserId", c.getCalleeUserId());
        m.put("caseId", c.getCaseId());
        m.put("clientId", c.getClientId());
        m.put("notes", c.getNotes());
        m.put("timeEntryId", c.getTimeEntryId());
        m.put("fromNumber", c.getFromNumber());
        m.put("toNumber", c.getToNumber());
        m.put("staffCallbackNumber", c.getStaffCallbackNumber());
        m.put("callerIdKind", c.getCallerIdKind());
        m.put("firmPhoneNumberId", c.getFirmPhoneNumberId());
        m.put("nonMatter", c.isNonMatter());
        m.put("twilioCallSid", c.getTwilioCallSid());
        return m;
    }

    private void closeCompleted(CallRecord rec, int seconds, String recordingUrl) {
        if (rec.getEndedAt() != null) {
            attachRecording(rec, recordingUrl);
            return;
        }
        int billedSeconds = Math.max(0, seconds);
        rec.setStatus("completed");
        rec.setEndedAt(Instant.now());
        rec.setDurationSeconds(billedSeconds);
        BigDecimal minutes = BigDecimal.valueOf(billedSeconds).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        rec.setTotalCost(rec.getCostPerMinute() == null
                ? BigDecimal.ZERO
                : rec.getCostPerMinute().multiply(minutes).setScale(4, RoundingMode.HALF_UP));
        if (recordingUrl != null && !recordingUrl.isBlank()) rec.setRecordingUrl(recordingUrl);

        if (rec.getCaseId() != null && billedSeconds > 0) {
            Note n = new Note();
            n.setTenantId(rec.getTenantId());
            n.setCaseId(rec.getCaseId());
            n.setUserId(rec.getCallerUserId());
            n.setTitle(rec.isRecordingEnabled() ? "Call recording (opt-in)" : "Call on the matter");
            StringBuilder bodyText = new StringBuilder();
            bodyText.append("Public network call. Duration ").append(billedSeconds).append("s. Caller ID ")
                    .append(rec.getFromNumber()).append(". PSTN invoices at month end.");
            if (rec.isRecordingEnabled()) bodyText.append(" Recording: opt-in, consent logged.");
            n.setBody(bodyText.toString());
            n.setType("call");
            notes.save(n);
        }

        if (billedSeconds > 0 && rec.isBillable() && rec.getCallerUserId() != null && rec.getCaseId() != null) {
            AppUser user = users.findById(rec.getCallerUserId()).orElse(null);
            if (user != null && rec.getTenantId().equals(user.getTenantId())) {
                int billedMinutes = Math.max(6, (int) Math.ceil(billedSeconds / 60.0));
                billedMinutes = ((billedMinutes + 5) / 6) * 6;
                TimeEntry t = new TimeEntry();
                t.setTenantId(rec.getTenantId());
                t.setUserId(user.getId());
                t.setCaseId(rec.getCaseId());
                t.setDate(LocalDate.now());
                t.setDurationMinutes(billedMinutes);
                t.setHourlyRate(user.getHourlyRate() == null ? new BigDecimal("350") : user.getHourlyRate());
                t.setTotalAmount(t.getHourlyRate().multiply(BigDecimal.valueOf(billedMinutes))
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                t.setDescription("Telephone conference");
                t.setActivityType("call");
                t.setSource("voice_call");
                t.setCallRecordId(rec.getId());
                timeEntries.save(t);
                rec.setTimeEntryId(t.getId());
            }
        }
        calls.save(rec);
    }

    private void closeUnsuccessful(CallRecord rec, String status) {
        if (rec.getEndedAt() != null) return;
        rec.setStatus(status);
        rec.setEndedAt(Instant.now());
        rec.setDurationSeconds(0);
        rec.setTotalCost(BigDecimal.ZERO);
        calls.save(rec);
    }

    private void attachRecording(CallRecord rec, String recordingUrl) {
        if (recordingUrl == null || recordingUrl.isBlank()) return;
        if (!rec.isRecordingEnabled() || !rec.isRecordingConsentGiven()) return;
        rec.setRecordingUrl(recordingUrl);
        calls.save(rec);
    }

    private void bindTenant(CallRecord rec) {
        TenantContext.setTenantId(rec.getTenantId());
        if (rec.getCallerUserId() != null) TenantContext.setUserId(rec.getCallerUserId());
    }

    private CallRecord requireBridge(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) {
            throw ApiException.notFound("Call not found");
        }
        return calls.findByBridgeToken(token).orElseThrow(() -> ApiException.notFound("Call not found"));
    }

    private String newToken() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static UUID uuidOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equals(text)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("That matter id is not valid.");
        }
    }

    private static int parseSeconds(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String mapCarrierStatus(String status) {
        return switch (status) {
            case "busy" -> "busy";
            case "no-answer", "noanswer" -> "missed";
            case "canceled", "cancelled" -> "canceled";
            case "completed", "answered" -> "completed";
            default -> "failed";
        };
    }

    private static String canonicalOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return raw.trim().startsWith("+") ? raw.trim() : E164.normalize(raw, "US");
        } catch (ApiException ex) {
            return null;
        }
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }
}
