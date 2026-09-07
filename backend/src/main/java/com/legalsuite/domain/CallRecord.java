package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "call_records")
@Getter
@Setter
@NoArgsConstructor
public class CallRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID clientId;
    private UUID callerUserId;
    private UUID calleeUserId;
    private String callType = "webrtc";
    private String direction = "internal";
    private String status = "initiated";
    private Instant startedAt = Instant.now();
    private Instant answeredAt;
    private Instant endedAt;
    private int durationSeconds;
    private BigDecimal costPerMinute = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private boolean recordingEnabled;
    private boolean recordingConsentGiven;
    private String recordingUrl;
    @Column(length = 4000)
    private String notes;
    private boolean billable = true;
    private UUID timeEntryId;
}
