package com.legalsuite.repo;

import com.legalsuite.domain.LandingPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingPageRepository extends JpaRepository<LandingPage, UUID> {
    Optional<LandingPage> findByTenantId(UUID tenantId);
}
