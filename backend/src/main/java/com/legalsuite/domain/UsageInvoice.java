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
@Table(name = "usage_invoices")
@Getter
@Setter
@NoArgsConstructor
public class UsageInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    @Column(nullable = false)
    private String period;
    private String invoiceNumber;
    private String status = "draft";
    private LocalDate dateIssued = LocalDate.now();
    private BigDecimal modulesSubtotal = BigDecimal.ZERO;
    private BigDecimal pstnSubtotal = BigDecimal.ZERO;
    private BigDecimal messagingSubtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    @Column(length = 8000)
    private String lineItemsJson;
    private Instant createdAt = Instant.now();
}
