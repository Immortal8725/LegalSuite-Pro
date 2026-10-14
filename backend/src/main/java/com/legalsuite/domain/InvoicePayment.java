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
@Table(name = "invoice_payments")
@Getter
@Setter
@NoArgsConstructor
public class InvoicePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private BigDecimal amount = BigDecimal.ZERO;
    /** cash, eft, card, other, or trust (trust only via trust-to-fee). */
    private String method;
    private LocalDate paidAt = LocalDate.now();
    @Column(length = 2000)
    private String note;
    private String reference;
    private UUID recordedBy;
    private UUID trustTransactionId;
    private UUID proofId;
    private Instant createdAt = Instant.now();
}
