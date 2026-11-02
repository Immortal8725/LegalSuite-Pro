package com.legalsuite.repo;

import com.legalsuite.domain.MatterMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatterMessageRepository extends JpaRepository<MatterMessage, UUID> {
    List<MatterMessage> findByTenantIdAndCaseIdOrderByCreatedAtAsc(UUID tenantId, UUID caseId);
    Optional<MatterMessage> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);
    List<MatterMessage> findByTenantIdAndSenderTypeAndReadByStaffAtIsNull(UUID tenantId, String senderType);
    long countByTenantIdAndCaseIdAndSenderTypeAndReadByStaffAtIsNull(UUID tenantId, UUID caseId, String senderType);
    long countByTenantIdAndCaseIdAndSenderTypeNotAndReadByClientAtIsNull(UUID tenantId, UUID caseId, String senderType);
}
