package com.legalsuite.repo;

import com.legalsuite.domain.TrustTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustTransactionRepository extends JpaRepository<TrustTransaction, UUID> {
    List<TrustTransaction> findByTenantIdAndTrustAccountIdOrderByCreatedAtDesc(UUID tenantId, UUID accountId);
}
