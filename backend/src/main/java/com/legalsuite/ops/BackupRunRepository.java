package com.legalsuite.ops;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupRunRepository extends JpaRepository<BackupRun, UUID> {
    Optional<BackupRun> findTopByStatusOrderByStartedAtDesc(String status);
    Optional<BackupRun> findTopByOrderByStartedAtDesc();
}
