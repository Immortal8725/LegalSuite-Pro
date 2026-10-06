package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Firm product-billing account. This is the LegalSuite seat, not a client fee invoice or a trust ledger. */
@Entity
@Table(name = "billing_accounts")
@Getter
@Setter
@NoArgsConstructor
public class BillingAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID tenantId;
    private int seatCount;
    /** inactive, pending, active, past_due, cancelled */
    private String seatStatus = "inactive";
    private Instant seatActiveUntil;
    private Instant lastPaymentAt;
    private Long lastAmountCents;
    @Column(length = 128)
    private String payfastToken;
    @Column(length = 64)
    private String lastPfPaymentId;
    @Column(name = "last_m_payment_id", length = 48)
    private String lastMerchantPaymentId;
    @Column(length = 16)
    private String environment = "sandbox";
    private Instant updatedAt = Instant.now();
}
