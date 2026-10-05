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
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID userId;
    private String category;
    @Column(length = 2000)
    private String description;
    private BigDecimal amount;
    private LocalDate date = LocalDate.now();
    private String vendor;
    private boolean billable = true;
    private boolean billed;
    private UUID invoiceId;
    private String status = "pending";
    private Instant createdAt = Instant.now();
}
