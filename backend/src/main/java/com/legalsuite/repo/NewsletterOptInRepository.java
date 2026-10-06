package com.legalsuite.repo;

import com.legalsuite.domain.NewsletterOptIn;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsletterOptInRepository extends JpaRepository<NewsletterOptIn, UUID> {
    Optional<NewsletterOptIn> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
