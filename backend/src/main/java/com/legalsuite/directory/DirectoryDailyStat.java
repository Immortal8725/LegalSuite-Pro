package com.legalsuite.directory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-firm daily counts: profile views and enquiries. No personal information. */
@Entity
@Table(
        name = "directory_daily_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_directory_daily_stat", columnNames = {"tenant_id", "stat_day"}))
@Getter
@Setter
@NoArgsConstructor
public class DirectoryDailyStat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "stat_day", nullable = false)
    private LocalDate statDay;

    private int profileViews;
    private int searchAppearances;
    private int enquiries;
}
