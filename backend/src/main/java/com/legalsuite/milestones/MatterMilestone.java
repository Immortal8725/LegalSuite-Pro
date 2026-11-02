package com.legalsuite.milestones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One line of a matter's fee plan. kind = deposit (fees in advance, into trust) or stage (fee earned at a litigation stage). */
@Entity
@Table(name = "matter_milestones")
@Getter
@Setter
@NoArgsConstructor
public class MatterMilestone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID caseId;
    private UUID clientId;
    @Column(nullable = false, length = 200)
    private String label;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(length = 80)
    private String stage;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private int sortOrder;
    /** pending, invoiced, paid, held (paid online but not yet posted to trust) */
    @Column(nullable = false, length = 20)
    private String status = "pending";
    private UUID invoiceId;
    @Column(length = 500)
    private String payUrl;
    private UUID trustTransactionId;
    private Instant triggeredAt;
    private Instant paidAt;
    private Instant createdAt;

    @PrePersist
    void persist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
