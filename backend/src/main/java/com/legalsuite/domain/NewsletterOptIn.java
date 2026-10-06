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
        name = "newsletter_opt_ins",
        uniqueConstraints = @UniqueConstraint(name = "uk_newsletter_opt_in", columnNames = {"tenant_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class NewsletterOptIn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String email;

    private boolean consent;
    private Instant consentedAt = Instant.now();
}
