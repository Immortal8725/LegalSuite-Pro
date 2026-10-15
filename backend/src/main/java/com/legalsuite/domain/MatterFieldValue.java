package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A document field value remembered on a matter (deceased name, estate number and so on) to pre-fill the next document. */
@Entity
@Table(name = "matter_field_values", uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
public class MatterFieldValue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;
    @Column(length = 2000)
    private String fieldValue;
    private Instant updatedAt = Instant.now();
}
