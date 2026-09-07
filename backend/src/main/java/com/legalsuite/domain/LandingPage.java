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
@Table(name = "landing_pages")
@Getter
@Setter
@NoArgsConstructor
public class LandingPage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true)
    private UUID tenantId;
    private String template = "classic";
    private String heroTitle;
    private String heroSubtitle;
    @Column(length = 8000)
    private String aboutText;
    @Column(length = 2000)
    private String colorsJson;
    @Column(length = 8000)
    private String testimonialsJson;
    private String seoTitle;
    @Column(length = 1000)
    private String seoDescription;
    private boolean published = true;
    private Instant updatedAt = Instant.now();
}
