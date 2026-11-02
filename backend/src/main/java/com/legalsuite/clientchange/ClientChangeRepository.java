package com.legalsuite.clientchange;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientChangeRepository extends JpaRepository<ClientChange, UUID> {
    List<ClientChange> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
    List<ClientChange> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
