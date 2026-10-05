package com.legalsuite.repo;

import com.legalsuite.domain.Contact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findByTenantIdOrderByLastNameAsc(UUID tenantId);
    Optional<Contact> findByIdAndTenantId(UUID id, UUID tenantId);
}
