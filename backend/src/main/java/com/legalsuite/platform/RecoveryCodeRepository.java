package com.legalsuite.platform;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {
    List<RecoveryCode> findByUserId(UUID userId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(UUID userId);
}
