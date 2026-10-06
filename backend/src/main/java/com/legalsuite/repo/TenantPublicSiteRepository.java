package com.legalsuite.repo;

import com.legalsuite.domain.TenantPublicSite;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPublicSiteRepository extends JpaRepository<TenantPublicSite, UUID> {
    Optional<TenantPublicSite> findByTenantId(UUID tenantId);
}
