package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    @Column(unique = true)
    private String slug;
    @Column(length = 1000)
    private String description;
    private BigDecimal priceMonthly;
    private Integer maxUsers;
    private Integer maxCases;
    private Integer maxStorageGb;
    private Integer includedVoiceMinutes;
    @Column(length = 4000)
    private String featuresJson;
    private Integer sortOrder;
    private boolean active = true;
    private Instant createdAt = Instant.now();
}
