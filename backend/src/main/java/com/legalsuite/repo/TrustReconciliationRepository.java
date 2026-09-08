package com.legalsuite.repo;

import com.legalsuite.domain.TrustReconciliation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustReconciliationRepository extends JpaRepository<TrustReconciliation, UUID> {
    List<TrustReconciliation> findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(UUID tenantId, UUID accountId);
    List<TrustReconciliation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
