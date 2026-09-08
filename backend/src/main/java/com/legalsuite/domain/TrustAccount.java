package com.legalsuite.domain;

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
@Table(name = "trust_accounts")
@Getter
@Setter
@NoArgsConstructor
public class TrustAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private String accountName;
    private String bankName;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal bankBalance;
    private Instant lastReconciledAt;
    private String accountType = "trust";
    private String status = "active";
    private Instant createdAt = Instant.now();
}
