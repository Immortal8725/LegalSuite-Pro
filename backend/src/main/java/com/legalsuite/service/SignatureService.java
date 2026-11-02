package com.legalsuite.service;

import com.legalsuite.common.Ids;
import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Note;
import com.legalsuite.domain.SignatureRequest;
import com.legalsuite.repo.NoteRepository;
import com.legalsuite.repo.SignatureRequestRepository;
import com.legalsuite.domain.DocumentFile;
import com.legalsuite.esign.SignedPdfBuilder;
import com.legalsuite.mail.MailGateway;
import com.legalsuite.mail.SmtpProperties;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentFileRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import com.legalsuite.storage.FileStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
    private final DocumentFileRepository documents;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final FileStorage storage;
    private final MailGateway mail;
    private final SmtpProperties smtp;
    private static final SecureRandom RNG = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private volatile String lastOtpForTests;

    public SignatureService(
            SignatureRequestRepository signatures,
            NoteRepository notes,
            AuditService audit,
            PracticeService practice,
            DocumentFileRepository documents,
            LegalCaseRepository cases,
            ClientRepository clients,
            TenantRepository tenants,
            FileStorage storage,
            MailGateway mail,
            SmtpProperties smtp) {
        this.signatures = signatures;
        this.notes = notes;
        this.audit = audit;
        this.practice = practice;
        this.documents = documents;
        this.cases = cases;
        this.clients = clients;
        this.tenants = tenants;
        this.storage = storage;
        this.mail = mail;
        this.smtp = smtp;
    }

    /** Staff: ask the matter's client (or a named signer) to sign an uploaded document. */
    @Transactional
    public Map<String, Object> requestForDocument(UUID documentId, Map<String, Object> body) {
        UUID tenantId = tid();
        DocumentFile doc = documents.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        byte[] bytes = readStored(doc);
        UUID caseId = doc.getCaseId();
        UUID clientId = doc.getClientId();
        if (clientId == null && caseId != null) {
            clientId = cases.findByIdAndTenantId(caseId, tenantId).map(c -> c.getClientId()).orElse(null);
        }
        var client = clientId == null ? null : clients.findByIdAndTenantId(clientId, tenantId).orElse(null);
        String name = str(body, "signerName", client == null ? "" : client.displayName()).trim();
        String email = str(body, "signerEmail", client == null || client.getEmail() == null ? "" : client.getEmail()).trim();
        if (name.isBlank()) throw ApiException.badRequest("Add the signer's name, or link the document to a client first.");
        boolean mandate = Boolean.parseBoolean(String.valueOf(body.getOrDefault("mandate", "false")));
        if (mandate && caseId == null) throw ApiException.badRequest("A mandate must be on a matter.");
        SignatureRequest s = new SignatureRequest();
        s.setTenantId(tenantId);
        s.setCaseId(caseId);
        s.setClientId(clientId);
        s.setDocumentId(doc.getId());
        s.setPurpose(mandate ? "engagement" : "document");
        s.setTitle(str(body, "title", doc.getName()));
        s.setDocumentBody(str(body, "message", "Please read and sign " + doc.getName() + "."));
        s.setSignerName(name);
        s.setSignerEmail(email);
        s.setStatus("pending");
        s.setDocumentHash(sha256Hex(bytes));
        signatures.save(s);
        audit.record("signature.request", "signature", s.getId().toString(), s.getTitle() + " > " + (email.isBlank() ? name : email));
        Map<String, Object> view = staffView(s);
        view.put("signUrl", "/sign/" + s.getId());
        return view;
    }

    /** Portal: signature requests for the signed-in client. */
    public List<Map<String, Object>> forClient(UUID clientId) {
        return signatures.findByTenantIdAndClientIdOrderByCreatedAtDesc(tid(), clientId).stream()
                .filter(s -> !"void".equals(s.getStatus()))
                .map(s -> { Map<String, Object> m = publicMap(s); m.put("signUrl", "/sign/" + s.getId()); return m; })
                .toList();
    }

    /** Public: email the signer a 6-digit code (ECTA evidence that the signer controls the address). */
    @Transactional
    public Map<String, Object> sendOtp(UUID id) {
        SignatureRequest s = signatures.findById(id).orElseThrow(() -> ApiException.notFound("Signature request not found"));
        if (!"pending".equals(s.getStatus())) throw ApiException.badRequest("This document is already " + s.getStatus());
        if (!otpRequired(s)) throw ApiException.badRequest("Email codes are not available for this request.");
        Instant now = Instant.now();
        if (s.getOtpExpiresAt() != null && s.getOtpExpiresAt().minus(OTP_TTL).plusSeconds(60).isAfter(now)) {
            throw ApiException.badRequest("A code was sent less than a minute ago. Check your email.");
        }
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        s.setOtpHash(otpHash(s.getId(), code));
        s.setOtpExpiresAt(now.plus(OTP_TTL));
        s.setOtpAttempts(0);
        signatures.save(s);
        lastOtpForTests = code;
        String firm = tenants.findById(s.getTenantId()).map(t -> t.getFirmName()).orElse("your law firm");
        mail.send(smtp.getFrom(), s.getSignerEmail(), "Your signing code: " + code,
                "Your code to sign \"" + s.getTitle() + "\" for " + firm + " is " + code + ".\n\n"
                        + "It expires in 10 minutes. If you did not ask for it, ignore this email.");
        audit.record("signature.otp", "signature", s.getId().toString(), "code sent");
        return Map.of("sent", true, "to", maskEmail(s.getSignerEmail()));
    }

    public String lastOtpForTests() { return lastOtpForTests; }

    private boolean otpRequired(SignatureRequest s) {
        return mail.configured() && s.getSignerEmail() != null && s.getSignerEmail().contains("@");
    }

    public List<Map<String, Object>> list() {
        return signatures.findByTenantIdOrderByCreatedAtDesc(tid()).stream().map(this::staffView).toList();
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        SignatureRequest s = new SignatureRequest();
        s.setTenantId(tid());
        if (body.get("caseId") != null) s.setCaseId(Ids.optUuid(body.get("caseId"), "matter"));
        if (body.get("clientId") != null) s.setClientId(Ids.optUuid(body.get("clientId"), "client"));
        if (body.get("leadId") != null) s.setLeadId(Ids.optUuid(body.get("leadId"), "lead"));
        if (body.get("conflictCheckId") != null) s.setConflictCheckId(Ids.optUuid(body.get("conflictCheckId"), "conflict check"));
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
        return sign(id, body, null, null);
    }

    @Transactional
    public Map<String, Object> sign(UUID id, Map<String, Object> body, String ip, String userAgent) {
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
        if (dataUrl.length() > 600_000) throw ApiException.badRequest("The signature image is too large.");
        String typed = str(body, "typedName", "").trim();
        if (typed.isBlank() && !"engagement".equals(s.getPurpose()) ) {
            throw ApiException.badRequest("Type your full name to sign.");
        }
        if (otpRequired(s)) {
            String code = str(body, "otp", "").trim();
            if (s.getOtpHash() == null || s.getOtpExpiresAt() == null) throw ApiException.badRequest("Ask for the email code first.");
            if (s.getOtpAttempts() >= OTP_MAX_ATTEMPTS) throw ApiException.badRequest("Too many wrong codes. Ask for a new code.");
            if (Instant.now().isAfter(s.getOtpExpiresAt())) throw ApiException.badRequest("The code has expired. Ask for a new code.");
            if (!MessageDigest.isEqual(otpHash(s.getId(), code).getBytes(StandardCharsets.UTF_8), s.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
                s.setOtpAttempts(s.getOtpAttempts() + 1);
                signatures.saveAndFlush(s);
                throw ApiException.badRequest("That code is not right. Check the latest email.");
            }
            s.setEmailVerifiedAt(Instant.now());
            s.setOtpHash(null);
        }
        s.setSignatureDataUrl(dataUrl);
        s.setStatus("signed");
        s.setSignedAt(Instant.now());
        if (body.get("signerName") != null) s.setSignerName(String.valueOf(body.get("signerName")));
        s.setTypedName(typed.isBlank() ? s.getSignerName() : typed);
        s.setSignerIp(trim(ip, 64));
        s.setSignerUserAgent(trim(userAgent, 500));
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
        storeSignedPdf(s);
        String purpose = s.getPurpose() == null ? "engagement" : s.getPurpose();
        if (!"engagement".equals(purpose) && s.getCaseId() != null) {
            Note n = new Note();
            n.setTenantId(s.getTenantId());
            n.setCaseId(s.getCaseId());
            n.setClientId(s.getClientId());
            n.setTitle("document".equals(purpose) ? "Document signed" : "Conflict waiver signed");
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

    /** Signed copy for whoever holds the signing link. */
    public byte[] signedPdf(UUID id) {
        SignatureRequest s = signatures.findById(id).orElseThrow(() -> ApiException.notFound("Signature request not found"));
        if (s.getSignedDocumentId() == null) throw ApiException.notFound("The signed copy is not ready.");
        DocumentFile d = documents.findByIdAndTenantId(s.getSignedDocumentId(), s.getTenantId())
                .orElseThrow(() -> ApiException.notFound("The signed copy is not ready."));
        return readStored(d);
    }

    /** The original document, so the signer can read it before signing. */
    public Map<String, Object> originalFile(UUID id) {
        SignatureRequest s = signatures.findById(id).orElseThrow(() -> ApiException.notFound("Signature request not found"));
        if (s.getDocumentId() == null || "void".equals(s.getStatus())) throw ApiException.notFound("No file on this request.");
        DocumentFile d = documents.findByIdAndTenantId(s.getDocumentId(), s.getTenantId())
                .orElseThrow(() -> ApiException.notFound("Document not found"));
        return Map.of("bytes", readStored(d), "name", d.getName(), "mime", d.getMimeType() == null ? "application/octet-stream" : d.getMimeType());
    }

    private void storeSignedPdf(SignatureRequest s) {
        DocumentFile source = s.getDocumentId() == null ? null
                : documents.findByIdAndTenantId(s.getDocumentId(), s.getTenantId()).orElse(null);
        byte[] sourcePdf = null;
        String text = s.getDocumentBody();
        if (source != null) {
            byte[] raw = readStored(source);
            String actual = sha256Hex(raw);
            if (!actual.equals(s.getDocumentHash())) {
                throw ApiException.badRequest("The document changed after the signature was requested. Ask the firm for a new request.");
            }
            boolean pdf = "application/pdf".equalsIgnoreCase(source.getMimeType()) || source.getName().toLowerCase().endsWith(".pdf");
            if (pdf) sourcePdf = raw;
            else text = "Signed file: " + source.getName() + "\nThe original file is kept unchanged on the matter. Its SHA-256 is on the audit certificate.\n\n" + (text == null ? "" : text);
        }
        String firm = tenants.findById(s.getTenantId()).map(t -> t.getFirmName()).orElse(null);
        var ev = new SignedPdfBuilder.Evidence(s.getTitle(), firm, s.getSignerName(), s.getTypedName(), s.getSignerEmail(),
                s.getSignerIdentityNumber(), s.getSignedAt(), s.getSignerIp(), s.getSignerUserAgent(),
                s.getDocumentHash(), s.getSignatureHash(), s.getId().toString(), s.getEmailVerifiedAt() != null, s.getEmailVerifiedAt());
        try {
            byte[] out = SignedPdfBuilder.build(sourcePdf, text, s.getSignatureDataUrl(), ev);
            String base = (source != null ? source.getName().replaceAll("(?i)\\.[a-z0-9]{1,5}$", "") : s.getTitle());
            String name = com.legalsuite.common.UploadPolicy.safeName(base + " (signed).pdf", "signed.pdf");
            String ref = storage.put(s.getTenantId() + "/" + com.legalsuite.common.UploadPolicy.storageName(name), out, "application/pdf");
            DocumentFile d = new DocumentFile();
            d.setTenantId(s.getTenantId());
            d.setCaseId(s.getCaseId());
            d.setClientId(s.getClientId());
            d.setName(name);
            d.setOriginalName(name);
            d.setCategory("signed");
            d.setMimeType("application/pdf");
            d.setSizeBytes(out.length);
            d.setStoragePath(ref);
            documents.save(d);
            s.setSignedDocumentId(d.getId());
            signatures.save(s);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not build the signed PDF. Try again.");
        }
    }

    private byte[] readStored(DocumentFile d) {
        if (d.getStoragePath() == null || d.getStoragePath().startsWith("seed://")) {
            throw ApiException.badRequest("This demo file has metadata only. Upload a real document first.");
        }
        try { return storage.read(d.getStoragePath()); } catch (IOException e) { throw ApiException.notFound("The file could not be read."); }
    }

    static String sha256Hex(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String otpHash(UUID id, String code) {
        return sha256Hex((id + ":" + code).getBytes(StandardCharsets.UTF_8));
    }

    private static String maskEmail(String e) {
        int at = e.indexOf('@');
        if (at <= 1) return "***" + e.substring(Math.max(0, at));
        return e.charAt(0) + "***" + e.substring(at);
    }

    private static String trim(String v, int max) {
        if (v == null) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    private Map<String, Object> staffView(SignatureRequest s) {
        Map<String, Object> m = publicMap(s);
        m.put("signerIp", s.getSignerIp());
        m.put("signerUserAgent", s.getSignerUserAgent());
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
        m.put("typedName", signed ? s.getTypedName() : null);
        m.put("documentId", s.getDocumentId());
        m.put("signedDocumentId", s.getSignedDocumentId());
        m.put("hasFile", s.getDocumentId() != null);
        m.put("emailConfirmed", s.getEmailVerifiedAt() != null);
        m.put("otpRequired", "pending".equals(s.getStatus()) && otpRequired(s));
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
