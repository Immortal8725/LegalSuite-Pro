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
@Table(name = "payment_proofs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentProof {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private UUID documentId;
    private BigDecimal amountClaimed = BigDecimal.ZERO;
    private String reference;
    @Column(length = 2000)
    private String note;
    /** pending_review, accepted, or rejected. */
    private String status = "pending_review";
    private UUID submittedBy;
    private Instant submittedAt = Instant.now();
    private UUID reviewedBy;
    private Instant reviewedAt;
    @Column(length = 2000)
    private String reviewNote;
    private UUID paymentId;
}
