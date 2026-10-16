package com.legalsuite.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A practitioner's LPC number and Fidelity Fund certificate, checked by hand by the Owlaw operator. */
@Entity
@Table(name = "practitioner_registrations")
@Getter
@Setter
@NoArgsConstructor
public class PractitionerRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, length = 64)
    private String lpcNumber;

    private LocalDate fidelityExpiresOn;

    /** pending, verified, or rejected. */
    @Column(nullable = false, length = 32)
    private String status = "pending";

    @Column(length = 500)
    private String verificationNote;

    private Instant verifiedAt;
    private UUID verifiedBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
