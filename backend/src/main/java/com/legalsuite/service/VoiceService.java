package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.TimeEntry;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceService {
    private final CallRecordRepository calls;
    private final TimeEntryRepository timeEntries;
    private final AppUserRepository users;
    private final Map<String, List<Map<String, Object>>> inbox = new ConcurrentHashMap<>();

    public VoiceService(CallRecordRepository calls, TimeEntryRepository timeEntries, AppUserRepository users) {
        this.calls = calls;
        this.timeEntries = timeEntries;
        this.users = users;
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
        rec.setRecordingConsentGiven(rec.isRecordingEnabled());
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
        if (body != null && body.get("recordingUrl") != null) rec.setRecordingUrl(String.valueOf(body.get("recordingUrl")));

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
        int seconds = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (CallRecord c : all) {
            seconds += c.getDurationSeconds();
            cost = cost.add(c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost());
            if ("webrtc".equals(c.getCallType())) webrtc++;
            else pstn++;
        }
        return Map.of(
                "totalCalls", all.size(),
                "webrtcCalls", webrtc,
                "pstnCalls", pstn,
                "totalMinutes", Math.round(seconds / 60.0),
                "totalCost", cost,
                "includedMinutes", 1000,
                "overageMinutes", 0,
                "note", "In-app WebRTC calls are free. PSTN minutes invoice at month end.");
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

    private BigDecimal costFor(String type) {
        return switch (type) {
            case "pstn_outbound" -> new BigDecimal("0.02");
            case "pstn_inbound" -> new BigDecimal("0.01");
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
        m.put("recordingUrl", c.getRecordingUrl());
        m.put("callerUserId", c.getCallerUserId());
        m.put("calleeUserId", c.getCalleeUserId());
        m.put("caseId", c.getCaseId());
        m.put("clientId", c.getClientId());
        m.put("notes", c.getNotes());
        m.put("timeEntryId", c.getTimeEntryId());
        return m;
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }
}
