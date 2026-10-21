package com.legalsuite.platform;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportGrantRepository extends JpaRepository<SupportGrant, UUID> {
    List<SupportGrant> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SupportGrant> findByRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAtAsc(java.time.Instant now);
}
