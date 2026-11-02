package com.legalsuite.clientchange;

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

/**
 * Trust money held on the wrong client's ledger moves to the right client only through a pair of trust entries
 * (out of one ledger, into the other). The request waits for a second person to approve it.
 */
@Entity
@Table(name = "trust_client_transfers")
@Getter
@Setter
@NoArgsConstructor
public class TrustClientTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    private UUID changeId;
    @Column(nullable = false)
    private UUID trustAccountId;
    private UUID caseId;
    @Column(nullable = false)
    private UUID fromClientId;
    @Column(nullable = false)
    private UUID toClientId;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 20)
    private String status = "pending";
    @Column(length = 1000)
    private String reason;
    private UUID requestedBy;
    private UUID decidedBy;
    private UUID outTransactionId;
    private UUID inTransactionId;
    private Instant createdAt = Instant.now();
    private Instant decidedAt;
}
