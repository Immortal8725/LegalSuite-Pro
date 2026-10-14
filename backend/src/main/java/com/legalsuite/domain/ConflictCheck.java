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

@Entity
@Table(name = "conflict_checks")
@Getter
@Setter
@NoArgsConstructor
public class ConflictCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private String searchName;
    private String status;
    private int matchCount;
    /** Full match list as JSON. Several hits run well past 255 characters, so this is unbounded text. */
    @Column(columnDefinition = "text")
    private String resultsJson;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
}
