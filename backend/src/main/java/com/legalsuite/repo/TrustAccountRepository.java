package com.legalsuite.repo;

import com.legalsuite.domain.TrustAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustAccountRepository extends JpaRepository<TrustAccount, UUID> {
    List<TrustAccount> findByTenantId(UUID tenantId);
    Optional<TrustAccount> findByIdAndTenantId(UUID id, UUID tenantId);
}
