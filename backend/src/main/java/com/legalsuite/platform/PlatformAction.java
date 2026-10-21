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
@Table(name = "platform_actions")
@Getter
@Setter
@NoArgsConstructor
public class PlatformAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID actorId;
    private String actorEmail;
    @Column(nullable = false, length = 128)
    private String action;
    @Column(length = 64)
    private String entityType;
    @Column(length = 64)
    private String entityId;
    @Column(length = 1000)
    private String detail;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
