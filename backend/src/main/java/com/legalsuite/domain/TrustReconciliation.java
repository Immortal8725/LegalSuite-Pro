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
@Table(name = "trust_reconciliations")
@Getter
@Setter
@NoArgsConstructor
public class TrustReconciliation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID trustAccountId;
    private LocalDate periodEnd = LocalDate.now();
    private BigDecimal bankBalance = BigDecimal.ZERO;
    private BigDecimal bookBalance = BigDecimal.ZERO;
    private BigDecimal clientLedgerTotal = BigDecimal.ZERO;
    private BigDecimal difference = BigDecimal.ZERO;
    private String status = "unbalanced";
    private boolean certified;
    private UUID certifiedBy;
    private Instant certifiedAt;
    @Column(length = 4000)
    private String notes;
    @Column(length = 8000)
    private String ledgersJson;
    private Instant createdAt = Instant.now();
}
