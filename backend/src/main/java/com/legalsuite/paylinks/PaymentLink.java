package com.legalsuite.paylinks;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_payment_links")
@Getter
@Setter
@NoArgsConstructor
public class PaymentLink {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID tenantId;
    private UUID invoiceId;
    private String token;
    private BigDecimal amount;
    private String mode;
    /** open, paid, cancelled */
    private String status = "open";
    private String pfPaymentId;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
    private Instant expiresAt;
    private Instant paidAt;
}
