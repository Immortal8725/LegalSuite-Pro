package com.legalsuite.repo;

import com.legalsuite.domain.FirmReferral;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmReferralRepository extends JpaRepository<FirmReferral, UUID> {
    List<FirmReferral> findByReferrerTenantIdOrderByCreatedAtDesc(UUID referrerTenantId);
    boolean existsByReferredTenantId(UUID referredTenantId);
}
