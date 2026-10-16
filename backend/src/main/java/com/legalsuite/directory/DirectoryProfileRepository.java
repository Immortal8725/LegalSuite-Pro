package com.legalsuite.directory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryProfileRepository extends JpaRepository<DirectoryProfile, UUID> {
    Optional<DirectoryProfile> findByTenantId(UUID tenantId);
}
