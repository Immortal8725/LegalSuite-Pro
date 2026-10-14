package com.legalsuite.repo;

import com.legalsuite.domain.BillingAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, UUID> {
    Optional<BillingAccount> findByTenantId(UUID tenantId);
}
