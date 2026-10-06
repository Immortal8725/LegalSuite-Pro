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

@Entity
@Table(name = "tenant_public_sites")
@Getter
@Setter
@NoArgsConstructor
public class TenantPublicSite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tenantId;

    /** draft, pending_approval, published, or rejected. */
    @Column(nullable = false)
    private String publishStatus = "draft";

    @Column(length = 1000)
    private String publishNote;

    @Column(length = 255)
    private String firmName;

    @Column(length = 500)
    private String tagline;

    /** Allowed accent key, not a free-form colour. */
    @Column(length = 32)
    private String accent = "navy";

    /** light or dark. Draft until branding approval. */
    @Column(nullable = false, length = 16)
    private String theme = "light";

    @Column(length = 4000)
    private String aboutText;

    @Column(length = 255)
    private String liveFirmName;

    @Column(length = 500)
    private String liveTagline;

    @Column(length = 32)
    private String liveAccent;

    @Column(length = 16)
    private String liveTheme;

    @Column(length = 4000)
    private String liveAboutText;

    /** none, pending, approved, or rejected. */
    @Column(nullable = false)
    private String brandingStatus = "none";

    @Column(length = 1000)
    private String brandingNote;

    @Column(length = 4000)
    private String recognitionJson;

    private Instant submittedAt;
    private Instant updatedAt = Instant.now();
}
