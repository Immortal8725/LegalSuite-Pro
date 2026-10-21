package com.legalsuite.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "trust_investments")
@Getter
@Setter
@NoArgsConstructor
public class TrustInvestment {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID tenantId;
    private UUID trustAccountId;
    private UUID clientId;
    private UUID caseId;
    /** "86(3)" or "86(4)". */
    private String section;
    private String institution;
    private String accountNumber;
    private BigDecimal principal = BigDecimal.ZERO;
    private BigDecimal interestEarned = BigDecimal.ZERO;
    private BigDecimal interestPaidOut = BigDecimal.ZERO;
    private LocalDate openedOn;
    private LocalDate closedOn;
    @Column(length = 1000)
    private String notes;
    private Instant createdAt = Instant.now();
}
