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
@Table(name = "invoice_write_offs")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceWriteOff {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private BigDecimal amount = BigDecimal.ZERO;
    @Column(length = 2000)
    private String reason;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
}
