package com.legalsuite.repo;

import com.legalsuite.domain.MatterStageEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatterStageEventRepository extends JpaRepository<MatterStageEvent, UUID> {
    List<MatterStageEvent> findByTenantIdAndCaseIdOrderByEnteredOnAscCreatedAtAsc(UUID tenantId, UUID caseId);
    void deleteByTenantIdAndCaseId(UUID tenantId, UUID caseId);
}
