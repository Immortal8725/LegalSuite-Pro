package com.legalsuite.repo;

import com.legalsuite.domain.DataSubjectRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {
    List<DataSubjectRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<DataSubjectRequest> findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(UUID tenantId, String subjectType, UUID subjectId);
    Optional<DataSubjectRequest> findByIdAndTenantId(UUID id, UUID tenantId);
}
