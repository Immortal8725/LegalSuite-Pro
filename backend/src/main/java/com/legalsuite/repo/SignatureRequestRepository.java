package com.legalsuite.repo;

import com.legalsuite.domain.SignatureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, UUID> {
    List<SignatureRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<SignatureRequest> findByIdAndTenantId(UUID id, UUID tenantId);
}
