package com.legalsuite.repo;

import com.legalsuite.domain.ConnectedIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectedIntegrationRepository extends JpaRepository<ConnectedIntegration, UUID> {
    List<ConnectedIntegration> findByTenantIdOrderByProviderAsc(UUID tenantId);
    Optional<ConnectedIntegration> findByTenantIdAndProvider(UUID tenantId, String provider);
}
