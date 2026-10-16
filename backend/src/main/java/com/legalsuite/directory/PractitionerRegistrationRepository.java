package com.legalsuite.directory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRegistrationRepository extends JpaRepository<PractitionerRegistration, UUID> {
    List<PractitionerRegistration> findByTenantIdOrderByFullNameAsc(UUID tenantId);

    Optional<PractitionerRegistration> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PractitionerRegistration> findByStatusOrderByCreatedAtAsc(String status);
}
