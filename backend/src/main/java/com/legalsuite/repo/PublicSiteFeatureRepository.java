package com.legalsuite.repo;

import com.legalsuite.domain.PublicSiteFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicSiteFeatureRepository extends JpaRepository<PublicSiteFeature, UUID> {
    List<PublicSiteFeature> findByTenantIdOrderByFeatureKeyAsc(UUID tenantId);

    Optional<PublicSiteFeature> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    List<PublicSiteFeature> findByApprovalStatus(String approvalStatus);
}
