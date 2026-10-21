package com.legalsuite.fica;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FicaProfileRepository extends JpaRepository<FicaProfile, UUID> {
    Optional<FicaProfile> findByTenantIdAndClientId(UUID tenantId, UUID clientId);
    List<FicaProfile> findByTenantId(UUID tenantId);
}
