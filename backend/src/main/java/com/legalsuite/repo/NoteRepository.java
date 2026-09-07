package com.legalsuite.repo;

import com.legalsuite.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
    List<Note> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
