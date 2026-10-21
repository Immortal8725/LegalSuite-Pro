package com.legalsuite.platform;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformActionRepository extends JpaRepository<PlatformAction, UUID> {
    List<PlatformAction> findTop500ByOrderByCreatedAtDesc();

    List<PlatformAction> findTop500ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    java.util.Optional<PlatformAction> findTopByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
