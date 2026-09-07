package com.legalsuite.repo;

import com.legalsuite.domain.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByDateIssuedDesc(UUID tenantId);
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantId(UUID tenantId);
}
