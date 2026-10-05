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

/** Phone-minute product invoice. Separate from client fee invoices and from the seat subscription. */
@Entity
@Table(name = "minutes_invoices")
@Getter
@Setter
@NoArgsConstructor
public class MinutesInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    @Column(nullable = false, length = 7)
    private String period;
    private int minutes;
    private long amountCents;
    /** issued, charged, failed, nothing_due */
    @Column(length = 16)
    private String status = "issued";
    @Column(name = "m_payment_id", length = 48)
    private String merchantPaymentId;
    @Column(length = 64)
    private String pfPaymentId;
    @Column(length = 500)
    private String note;
    private Instant createdAt = Instant.now();
    private Instant chargedAt;
}
