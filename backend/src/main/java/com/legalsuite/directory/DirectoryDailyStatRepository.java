package com.legalsuite.directory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryDailyStatRepository extends JpaRepository<DirectoryDailyStat, UUID> {
    Optional<DirectoryDailyStat> findByTenantIdAndStatDay(UUID tenantId, LocalDate statDay);

    List<DirectoryDailyStat> findByTenantIdAndStatDayGreaterThanEqualOrderByStatDayAsc(UUID tenantId, LocalDate from);
}
