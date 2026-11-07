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

/** A firm that signed up with another firm's invite code. No money is stored here. */
@Entity
@Table(name = "firm_referrals")
@Getter
@Setter
@NoArgsConstructor
public class FirmReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID referrerTenantId;
    @Column(nullable = false, unique = true)
    private UUID referredTenantId;
    private String referredFirmName;
    private Instant createdAt = Instant.now();
}
