package com.legalsuite.repo;

import com.legalsuite.domain.Lead;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Lead> findByIdAndTenantId(UUID id, UUID tenantId);
}
