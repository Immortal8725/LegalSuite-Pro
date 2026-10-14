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
@Table(name = "product_checkouts")
@Getter
@Setter
@NoArgsConstructor
public class ProductCheckout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    @Column(name = "m_payment_id", nullable = false, unique = true, length = 48)
    private String merchantPaymentId;
    /** seat, token, or minutes */
    @Column(length = 16)
    private String kind;
    private int seatCount;
    private long amountCents;
    @Column(length = 16)
    private String status = "pending";
    @Column(length = 16)
    private String period;
    private Instant createdAt = Instant.now();
}
