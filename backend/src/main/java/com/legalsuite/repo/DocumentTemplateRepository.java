package com.legalsuite.repo;

import com.legalsuite.domain.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
    List<DocumentTemplate> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<DocumentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
