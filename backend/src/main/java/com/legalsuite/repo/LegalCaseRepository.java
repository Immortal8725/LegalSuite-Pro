package com.legalsuite.repo;

import com.legalsuite.domain.LegalCase;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalCaseRepository extends JpaRepository<LegalCase, UUID> {
    List<LegalCase> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<LegalCase> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<LegalCase> findByTenantIdAndCaseNumberIgnoreCase(UUID tenantId, String caseNumber);
    List<LegalCase> findByTenantIdAndClientId(UUID tenantId, UUID clientId);
    long countByTenantId(UUID tenantId);
    long countByTenantIdAndStatusNotIn(UUID tenantId, Collection<String> statuses);
}
