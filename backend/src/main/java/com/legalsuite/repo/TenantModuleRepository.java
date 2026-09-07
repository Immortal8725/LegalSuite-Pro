package com.legalsuite.repo;

import com.legalsuite.domain.TenantModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {
    List<TenantModule> findByTenantId(UUID tenantId);
    Optional<TenantModule> findByTenantIdAndModuleId(UUID tenantId, UUID moduleId);
}
