package com.legalsuite.repo;

import com.legalsuite.domain.FirmPhoneNumber;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmPhoneNumberRepository extends JpaRepository<FirmPhoneNumber, UUID> {
    List<FirmPhoneNumber> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<FirmPhoneNumber> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FirmPhoneNumber> findFirstByTenantIdAndDefaultOutboundTrueAndStatus(UUID tenantId, String status);

    List<FirmPhoneNumber> findByE164AndStatusIn(String e164, Collection<String> statuses);
}
