package com.legalsuite.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_requests")
public class SignatureRequest {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    private UUID caseId;
    private UUID clientId;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String documentBody;
    @Column(nullable = false)
    private String signerName;
    @Column(nullable = false)
    private String signerEmail;
    @Column(nullable = false)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String signatureDataUrl;
    private Instant signedAt;
    private Instant createdAt;
    private String purpose;
    private UUID leadId;
    private UUID conflictCheckId;
    @Column(length = 128)
    private String documentHash;
    @Column(length = 128)
    private String signatureHash;

    @PrePersist
    void persist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "pending";
        if (purpose == null) purpose = "engagement";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDocumentBody() { return documentBody; }
    public void setDocumentBody(String documentBody) { this.documentBody = documentBody; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public UUID getLeadId() { return leadId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public UUID getConflictCheckId() { return conflictCheckId; }
    public void setConflictCheckId(UUID conflictCheckId) { this.conflictCheckId = conflictCheckId; }
    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }
    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String signatureHash) { this.signatureHash = signatureHash; }
}
