package com.legalsuite.milestones;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatterMilestoneRepository extends JpaRepository<MatterMilestone, UUID> {
    List<MatterMilestone> findByTenantIdAndCaseIdOrderBySortOrderAsc(UUID tenantId, UUID caseId);
    List<MatterMilestone> findByTenantIdAndClientIdOrderByCreatedAtAsc(UUID tenantId, UUID clientId);
    Optional<MatterMilestone> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<MatterMilestone> findFirstByInvoiceId(UUID invoiceId);
}
