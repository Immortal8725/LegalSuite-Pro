package com.legalsuite.directory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryEnquiryRepository extends JpaRepository<DirectoryEnquiry, UUID> {
    List<DirectoryEnquiry> findByPurgeAfterBefore(Instant cutoff);

    Optional<DirectoryEnquiry> findByLeadId(UUID leadId);
}
