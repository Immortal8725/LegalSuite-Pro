package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "public_site_features",
        uniqueConstraints = @UniqueConstraint(name = "uk_public_site_feature", columnNames = {"tenant_id", "feature_key"}))
@Getter
@Setter
@NoArgsConstructor
public class PublicSiteFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    /** Tenant asked for the feature to be on. */
    private boolean requested;

    /** none, pending, approved, or rejected. */
    @Column(nullable = false, length = 32)
    private String approvalStatus = "none";

    @Column(length = 1000)
    private String note;

    private Instant updatedAt = Instant.now();
}
