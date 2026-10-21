package com.legalsuite.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POPIA record for an enquiry sent through the directory. The enquiry itself is a lead in the firm's intake. */
@Entity
@Table(name = "directory_enquiries")
@Getter
@Setter
@NoArgsConstructor
public class DirectoryEnquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    private UUID conflictCheckId;

    /** clear or potential_conflict. */
    @Column(length = 32)
    private String conflictStatus;

    @Column(length = 64)
    private String practiceArea;

    private Instant consentAt;

    @Column(length = 32)
    private String consentVersion;

    private Instant createdAt = Instant.now();

    /** Deleted after this unless the lead became a client. */
    private Instant purgeAfter;
}
