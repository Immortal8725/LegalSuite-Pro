package com.legalsuite.repo;

import com.legalsuite.domain.UsageInvoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageInvoiceRepository extends JpaRepository<UsageInvoice, UUID> {
    List<UsageInvoice> findByTenantIdOrderByPeriodDesc(UUID tenantId);
    Optional<UsageInvoice> findByTenantIdAndPeriod(UUID tenantId, String period);
}
