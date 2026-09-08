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
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
public class LegalCase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID clientId;
    private String caseNumber;
    private String title;
    @Column(length = 4000)
    private String description;
    private String caseType;
    private String practiceArea;
    private String status = "open";
    private String priority = "medium";
    private String courtName;
    private String courtCaseNumber;
    private String judgeName;
    private String opposingParty;
    private String opposingCounsel;
    private UUID leadAttorneyId;
    private String billingType = "hourly";
    private BigDecimal billingRate;
    private BigDecimal budget;
    private LocalDate dateOpened = LocalDate.now();
    private LocalDate dateClosed;
    private LocalDate statuteOfLimitations;
    private LocalDate accrualDate;
    private LocalDate discoveryDate;
    private LocalDate plaintiffDob;
    private LocalDate probateOpened;
    private boolean governmentalDefendant;
    private String docketTrack;
    private String controllingKind;
    private String solRuleId;
    @Column(length = 500)
    private String solCitation;
    @Column(length = 2000)
    private String solReason;
    @Column(length = 4000)
    private String docketClocksJson;
    @Column(length = 2000)
    private String tagsJson;
    private boolean conflictChecked;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
