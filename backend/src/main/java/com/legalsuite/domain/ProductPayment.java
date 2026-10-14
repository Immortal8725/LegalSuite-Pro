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

@Entity
@Table(name = "product_payments")
@Getter
@Setter
@NoArgsConstructor
public class ProductPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    /** seat, token, or minutes */
    @Column(length = 16)
    private String kind;
    @Column(name = "m_payment_id", length = 48)
    private String merchantPaymentId;
    @Column(unique = true, length = 64)
    private String pfPaymentId;
    @Column(length = 128)
    private String token;
    private long amountCents;
    @Column(length = 32)
    private String paymentStatus;
    @Column(length = 16)
    private String period;
    private Instant createdAt = Instant.now();
}
