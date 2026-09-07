package com.legalsuite.repo;

import com.legalsuite.domain.DocumentFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, UUID> {
    List<DocumentFile> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<DocumentFile> findByIdAndTenantId(UUID id, UUID tenantId);
    List<DocumentFile> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);
}
