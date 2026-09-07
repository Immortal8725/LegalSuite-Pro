package com.legalsuite.repo;

import com.legalsuite.domain.ConflictCheck;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConflictCheckRepository extends JpaRepository<ConflictCheck, UUID> {
    List<ConflictCheck> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ConflictCheck> findByIdAndTenantId(UUID id, UUID tenantId);
}
