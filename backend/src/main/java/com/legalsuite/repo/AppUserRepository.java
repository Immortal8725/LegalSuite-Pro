package com.legalsuite.repo;

import com.legalsuite.domain.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    Optional<AppUser> findByIdAndTenantId(UUID id, UUID tenantId);
    List<AppUser> findByTenantIdOrderByLastNameAsc(UUID tenantId);
    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    long countByTenantId(UUID tenantId);
}
