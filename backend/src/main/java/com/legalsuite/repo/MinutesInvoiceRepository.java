package com.legalsuite.repo;

import com.legalsuite.domain.MinutesInvoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinutesInvoiceRepository extends JpaRepository<MinutesInvoice, UUID> {
    Optional<MinutesInvoice> findByTenantIdAndPeriod(UUID tenantId, String period);
    Optional<MinutesInvoice> findByMerchantPaymentId(String merchantPaymentId);
    List<MinutesInvoice> findByTenantIdOrderByPeriodDesc(UUID tenantId);
}
