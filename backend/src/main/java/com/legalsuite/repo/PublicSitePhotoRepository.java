package com.legalsuite.repo;

import com.legalsuite.domain.PublicSitePhoto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicSitePhotoRepository extends JpaRepository<PublicSitePhoto, UUID> {
    Optional<PublicSitePhoto> findByTenantIdAndSlot(UUID tenantId, String slot);

    List<PublicSitePhoto> findByTenantId(UUID tenantId);
}
