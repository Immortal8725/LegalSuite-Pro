package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.SignatureRequest;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.SignatureRequestRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignatureService {
    private final SignatureRequestRepository signatures;
    private final NoteRepository notes;
    private final AuditService audit;
    private final PracticeService practice;

    public SignatureService(
            SignatureRequestRepository signatures,
            NoteRepository notes,
            AuditService audit,
            PracticeService practice) {
        this.signatures = signatures;
        this.notes = notes;
        this.audit = audit;
        this.practice = practice;
    }

    public List<Map<String, Object>> list() {
        return signatures.findByTenantIdOrderByCreatedAtDesc(tid()).stream().map(this::staffView).toList();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        SignatureRequest s = new SignatureRequest();
        s.setTenantId(tid());
        if (body.get("caseId") != null) s.setCaseId(UUID.fromString(String.valueOf(body.get("caseId"))));
        if (body.get("clientId") != null) s.setClientId(UUID.fromString(String.valueOf(body.get("clientId"))));
        if (body.get("leadId") != null) s.setLeadId(UUID.fromString(String.valueOf(body.get("leadId"))));
        if (body.get("conflictCheckId") != null) s.setConflictCheckId(UUID.fromString(String.valueOf(body.get("conflictCheckId"))));
        s.setPurpose(str(body, "purpose", "engagement"));
        s.setTitle(str(body, "title", "Engagement letter"));
        s.setDocumentBody(str(body, "documentBody", ""));
        s.setSignerName(str(body, "signerName", "Signer"));
        s.setSignerEmail(str(body, "signerEmail", ""));
        s.setStatus("pending");
        s.setDocumentHash(DocumentHash.sha256(s.getDocumentBody()));
        signatures.save(s);
        audit.record("signature.create", "signature", s.getId().toString(), s.getTitle() + " → " + s.getSignerEmail());
        Map<String, Object> view = staffView(s);
        view.put("signUrl", "/sign/" + s.getId());
        return view;
    }

    public Map<String, Object> instrumentView(SignatureRequest s) {
        return staffView(s);
    }

    public Map<String, Object> publicView(UUID id) {
        SignatureRequest s = signatures.findById(id).orElseThrow(() -> ApiException.notFound("Signature request not found"));
        return publicMap(s);
    }

    @Transactional
    public Map<String, Object> sign(UUID id, Map<String, Object> body) {
        SignatureRequest s = signatures.findById(id).orElseThrow(() -> ApiException.notFound("Signature request not found"));
        if (!"pending".equals(s.getStatus())) {
            throw ApiException.badRequest("This document is already " + s.getStatus());
        }
        String dataUrl = str(body, "signatureDataUrl", "");
        if (dataUrl.isBlank()) {
            throw ApiException.badRequest("Draw or type a signature first");
        }
        String identity = str(body, "signerIdentityNumber", "").trim();
        if (identity.length() < 6) {
            throw ApiException.badRequest("ECT Act s 13: enter the signer's identity number (SA ID, passport, or equivalent).");
        }
        s.setSignatureDataUrl(dataUrl);
        s.setStatus("signed");
        s.setSignedAt(Instant.now());
        if (body.get("signerName") != null) s.setSignerName(String.valueOf(body.get("signerName")));
        s.setSignerIdentityNumber(identity);
        s.setSignatureStandard("ect_act_25_2002_s13");
        s.setSignatureHash(DocumentHash.sha256(
                s.getDocumentBody(),
                s.getSignerName(),
                identity,
                s.getSignedAt().toString(),
                dataUrl.substring(0, Math.min(80, dataUrl.length()))));
        signatures.save(s);
        TenantContext.setTenantId(s.getTenantId());
        String purpose = s.getPurpose() == null ? "engagement" : s.getPurpose();
        if (!"engagement".equals(purpose) && s.getCaseId() != null) {
            Note n = new Note();
            n.setTenantId(s.getTenantId());
            n.setCaseId(s.getCaseId());
            n.setClientId(s.getClientId());
            n.setTitle("Conflict waiver signed");
            n.setBody(s.getSignerName() + " signed “" + s.getTitle() + "”. Hash " + s.getSignatureHash() + ".");
            n.setType("esign");
            notes.save(n);
        }
        if ("engagement".equals(purpose) && s.getCaseId() != null) {
            practice.activateEngagement(s.getCaseId(), s.getId(), s.getSignatureHash());
        }
        Map<String, Object> view = publicMap(s);
        view.put("unlocked", "engagement".equals(purpose) && s.getCaseId() != null);
        return view;
    }

    @Transactional
    public Map<String, Object> voidRequest(UUID id) {
        SignatureRequest s = signatures.findByIdAndTenantId(id, tid())
                .orElseThrow(() -> ApiException.notFound("Signature request not found"));
        s.setStatus("void");
        signatures.save(s);
        audit.record("signature.void", "signature", s.getId().toString(), s.getTitle());
        return staffView(s);
    }

    private Map<String, Object> staffView(SignatureRequest s) {
        Map<String, Object> m = publicMap(s);
        m.put("caseId", s.getCaseId());
        m.put("clientId", s.getClientId());
        m.put("signUrl", "/sign/" + s.getId());
        return m;
    }

    private Map<String, Object> publicMap(SignatureRequest s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("documentBody", s.getDocumentBody());
        m.put("signerName", s.getSignerName());
        m.put("signerEmail", s.getSignerEmail());
        m.put("status", s.getStatus());
        m.put("signedAt", s.getSignedAt());
        m.put("createdAt", s.getCreatedAt());
        boolean signed = "signed".equals(s.getStatus());
        m.put("signatureDataUrl", signed ? s.getSignatureDataUrl() : null);
        m.put("purpose", s.getPurpose() == null ? "engagement" : s.getPurpose());
        m.put("documentHash", s.getDocumentHash());
        m.put("signatureHash", signed ? s.getSignatureHash() : null);
        m.put("leadId", s.getLeadId());
        m.put("signatureStandard", s.getSignatureStandard());
        m.put("identityCaptured", s.getSignerIdentityNumber() != null && !s.getSignerIdentityNumber().isBlank());
        return m;
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private static String str(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v == null ? fallback : String.valueOf(v);
    }
}
