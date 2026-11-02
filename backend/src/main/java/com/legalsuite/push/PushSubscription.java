package com.legalsuite.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A browser or phone that agreed to receive Web Push notifications. */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    private UUID userId;
    private UUID clientId;
    @Column(nullable = false, length = 1000)
    private String endpoint;
    @Column(nullable = false, length = 200)
    private String p256dh;
    @Column(nullable = false, length = 100)
    private String auth;
    @Column(length = 300)
    private String userAgent;
    private Instant createdAt;
    private Instant lastSuccessAt;
    @Column(nullable = false)
    private int failures;

    @PrePersist
    void persist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
