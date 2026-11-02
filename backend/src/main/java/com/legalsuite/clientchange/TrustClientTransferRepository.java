package com.legalsuite.clientchange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustClientTransferRepository extends JpaRepository<TrustClientTransfer, UUID> {
    List<TrustClientTransfer> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<TrustClientTransfer> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
    Optional<TrustClientTransfer> findByIdAndTenantId(UUID id, UUID tenantId);
}
