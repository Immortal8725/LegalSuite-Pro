package com.legalsuite.repo;

import com.legalsuite.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserId(UUID userId);

    /** Consume a refresh token. Returns 0 when another request already used it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken r where r.id = :id")
    int consume(@Param("id") UUID id);
}
