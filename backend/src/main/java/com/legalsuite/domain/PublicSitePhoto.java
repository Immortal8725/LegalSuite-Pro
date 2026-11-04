package com.legalsuite.domain;

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

/** One photograph slot on a firm's public site: "hero" or "person-&lt;userId&gt;". */
@Entity
@Table(name = "public_site_photos")
@Getter
@Setter
@NoArgsConstructor
public class PublicSitePhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID tenantId;
    private String slot;
    private String draftRef;
    private String draftType;
    private Long draftVersion;
    private String liveRef;
    private String liveType;
    private Long liveVersion;
    private Instant updatedAt = Instant.now();
}
