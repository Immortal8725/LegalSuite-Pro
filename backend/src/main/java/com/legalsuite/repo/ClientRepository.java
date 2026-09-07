package com.legalsuite.repo;

import com.legalsuite.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByTenantIdOrderByLastNameAsc(UUID tenantId);
    Optional<Client> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Client> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    long countByTenantId(UUID tenantId);
}
