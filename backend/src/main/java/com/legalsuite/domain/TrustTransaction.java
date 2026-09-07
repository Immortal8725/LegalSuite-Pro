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
@Table(name = "trust_transactions")
@Getter
@Setter
@NoArgsConstructor
public class TrustTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID trustAccountId;
    private UUID clientId;
    private UUID caseId;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    @Column(length = 2000)
    private String description;
    private String referenceNumber;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
}
