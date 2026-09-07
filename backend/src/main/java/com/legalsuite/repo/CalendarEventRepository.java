package com.legalsuite.repo;

import com.legalsuite.domain.CalendarEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findByTenantIdAndStartTimeBetweenOrderByStartTimeAsc(UUID tenantId, Instant from, Instant to);
    List<CalendarEvent> findByTenantIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(UUID tenantId, Instant from);
}
