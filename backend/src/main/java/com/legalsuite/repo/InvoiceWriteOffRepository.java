package com.legalsuite.repo;

import com.legalsuite.domain.InvoiceWriteOff;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceWriteOffRepository extends JpaRepository<InvoiceWriteOff, UUID> {
    List<InvoiceWriteOff> findByTenantIdAndInvoiceIdOrderByCreatedAtAsc(UUID tenantId, UUID invoiceId);
}
