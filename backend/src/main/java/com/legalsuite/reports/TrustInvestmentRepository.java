package com.legalsuite.reports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustInvestmentRepository extends JpaRepository<TrustInvestment, UUID> {
    List<TrustInvestment> findByTenantIdOrderByOpenedOnDesc(UUID tenantId);
    Optional<TrustInvestment> findByIdAndTenantId(UUID id, UUID tenantId);
}
