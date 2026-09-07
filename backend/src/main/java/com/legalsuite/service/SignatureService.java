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

    public SignatureService(SignatureRequestRepository signatures, NoteRepository notes, AuditService audit) {
        this.signatures = signatures;
        this.notes = notes;
        this.audit = audit;
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
        s.setTitle(str(body, "title", "Engagement letter"));
        s.setDocumentBody(str(body, "documentBody", ""));
        s.setSignerName(str(body, "signerName", "Signer"));
        s.setSignerEmail(str(body, "signerEmail", ""));
        s.setStatus("pending");
        signatures.save(s);
        audit.record("signature.create", "signature", s.getId().toString(), s.getTitle() + " → " + s.getSignerEmail());
        Map<String, Object> view = staffView(s);
        view.put("signUrl", "/sign/" + s.getId());
        return view;
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
        s.setSignatureDataUrl(dataUrl);
        s.setStatus("signed");
        s.setSignedAt(Instant.now());
        if (body.get("signerName") != null) s.setSignerName(String.valueOf(body.get("signerName")));
        signatures.save(s);
        if (s.getCaseId() != null) {
            Note n = new Note();
            n.setTenantId(s.getTenantId());
            n.setCaseId(s.getCaseId());
            n.setClientId(s.getClientId());
            n.setTitle("Engagement signed");
            n.setBody(s.getSignerName() + " signed “" + s.getTitle() + "”. The wet ink lives on this tenant.");
            n.setType("esign");
            notes.save(n);
        }
        return publicMap(s);
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
