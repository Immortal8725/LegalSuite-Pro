package com.legalsuite.platform;

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
@Table(name = "support_grants")
@Getter
@Setter
@NoArgsConstructor
public class SupportGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID grantedBy;
    private String grantedByEmail;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false, length = 32)
    private String scope = "read_only";
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokedByEmail;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public boolean activeAt(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
