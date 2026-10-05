package com.legalsuite.repo;

import com.legalsuite.domain.CallRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallRecordRepository extends JpaRepository<CallRecord, UUID> {
    List<CallRecord> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
    Optional<CallRecord> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<CallRecord> findByBridgeToken(String bridgeToken);
    Optional<CallRecord> findByTwilioCallSid(String twilioCallSid);
}
