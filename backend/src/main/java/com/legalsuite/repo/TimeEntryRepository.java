package com.legalsuite.repo;

import com.legalsuite.domain.TimeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    List<TimeEntry> findByTenantIdOrderByDateDesc(UUID tenantId);
    List<TimeEntry> findByTenantIdAndBilledFalseAndBillableTrue(UUID tenantId);
    List<TimeEntry> findByTenantIdAndUserIdOrderByDateDesc(UUID tenantId, UUID userId);
}
