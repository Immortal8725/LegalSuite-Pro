package com.legalsuite.fica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fica_profiles")
@Getter
@Setter
@NoArgsConstructor
public class FicaProfile {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID tenantId;
    private UUID clientId;
    private String status = "incomplete";
    private String riskRating;
    private Integer riskScore;
    @Column(length = 2000)
    private String riskFactorsJson;
    private String idType;
    private String idNumber;
    private LocalDate idExpiry;
    private UUID idDocumentId;
    private LocalDate poaDate;
    private UUID poaDocumentId;
    private String pepStatus;
    private Instant sanctionsCheckedAt;
    private String sanctionsResult;
    @Column(length = 1000)
    private String sanctionsSources;
    @Column(length = 1000)
    private String sourceOfFunds;
    @Column(length = 4000)
    private String checklistJson;
    private UUID verifiedBy;
    private Instant verifiedAt;
    private LocalDate nextReviewDate;
    @Column(length = 4000)
    private String notes;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
