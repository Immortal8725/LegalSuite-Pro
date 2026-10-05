package com.legalsuite.repo;

import com.legalsuite.domain.OutboundMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, UUID> {
    List<OutboundMessage> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<OutboundMessage> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);

    Optional<OutboundMessage> findByCallbackToken(String callbackToken);
}
