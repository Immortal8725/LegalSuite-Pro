package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A POPIA data subject request (s 23 access, s 24 correction or deletion) waiting for, or decided by, a firm admin. */
@Entity
@Table(name = "data_subject_requests")
@Getter
@Setter
@NoArgsConstructor
public class DataSubjectRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    /** client or user */
    private String subjectType;
    private UUID subjectId;
    private String subjectName;
    /** erasure (export is self-service and logged, not queued) */
    private String kind = "erasure";
    /** pending, completed or rejected */
    private String status = "pending";
    private UUID requestedBy;
    private String requestedByRole;
    @Column(length = 2000)
    private String reason;
    @Column(length = 2000)
    private String decisionNote;
    private UUID decidedBy;
    @Column(length = 4000)
    private String outcome;
    private Instant createdAt = Instant.now();
    private Instant decidedAt;
}
