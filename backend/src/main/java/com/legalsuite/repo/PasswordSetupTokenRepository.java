package com.legalsuite.repo;

import com.legalsuite.domain.PasswordSetupToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, UUID> {
    Optional<PasswordSetupToken> findByTokenHash(String tokenHash);
    List<PasswordSetupToken> findByUserIdAndUsedAtIsNull(UUID userId);
}
