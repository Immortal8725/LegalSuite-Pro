package com.legalsuite.repo;

import com.legalsuite.domain.MatterFieldValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatterFieldValueRepository extends JpaRepository<MatterFieldValue, UUID> {
    List<MatterFieldValue> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);
    Optional<MatterFieldValue> findByTenantIdAndCaseIdAndFieldKey(UUID tenantId, UUID caseId, String fieldKey);
}
