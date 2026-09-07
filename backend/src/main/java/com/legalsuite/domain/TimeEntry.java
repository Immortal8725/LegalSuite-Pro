package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "time_entries")
@Getter
@Setter
@NoArgsConstructor
public class TimeEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID userId;
    private LocalDate date = LocalDate.now();
    private int durationMinutes;
    private BigDecimal hourlyRate;
    private BigDecimal totalAmount;
    @Column(length = 2000)
    private String description;
    private String activityType;
    private boolean billable = true;
    private boolean billed;
    private UUID invoiceId;
    private String source = "manual";
    private UUID callRecordId;
    private Instant createdAt = Instant.now();
}
