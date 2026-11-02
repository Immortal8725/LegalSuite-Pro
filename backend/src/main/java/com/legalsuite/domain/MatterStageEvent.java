package com.legalsuite.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step on a matter's litigation timeline. */
@Entity
@Table(name = "matter_stage_events")
@Getter
@Setter
@NoArgsConstructor
public class MatterStageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private String track;
    private String stage;
    private LocalDate enteredOn;
    private String note;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
}
