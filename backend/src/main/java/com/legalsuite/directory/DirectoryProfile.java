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

/** A firm's Find a Lawyer listing. One per tenant. Lists are JSON arrays of catalogue slugs. */
@Entity
@Table(name = "directory_profiles")
@Getter
@Setter
@NoArgsConstructor
public class DirectoryProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(length = 2000)
    private String practiceAreasJson = "[]";

    @Column(length = 2000)
    private String provincesJson = "[]";

    @Column(length = 2000)
    private String citiesJson = "[]";

    @Column(length = 2000)
    private String languagesJson = "[]";

    @Column(length = 2000)
    private String consultOptionsJson = "[]";

    @Column(precision = 10, scale = 2)
    private BigDecimal fixedConsultFee;

    @Column(length = 32)
    private String responseTime;

    @Column(length = 600)
    private String bio;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
