package com.legalsuite.repo;

import com.legalsuite.domain.AppNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {
    List<AppNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);
    long countByTenantIdAndUserIdAndReadFalse(UUID tenantId, UUID userId);
}
