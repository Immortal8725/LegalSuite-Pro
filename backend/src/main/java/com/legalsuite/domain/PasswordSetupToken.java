package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One-time link that lets an invited user choose their own password. Only the SHA-256 of the token is stored. */
@Entity
@Table(name = "password_setup_tokens", indexes = @Index(name = "idx_password_setup_tokens_user", columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
public class PasswordSetupToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 32)
    private String purpose = "invite";
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt = Instant.now();
}
