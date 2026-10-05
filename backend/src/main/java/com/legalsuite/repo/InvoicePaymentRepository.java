package com.legalsuite.repo;

import com.legalsuite.domain.InvoicePayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findByTenantIdAndInvoiceIdOrderByPaidAtAscCreatedAtAsc(UUID tenantId, UUID invoiceId);
    Optional<InvoicePayment> findByTenantIdAndProofId(UUID tenantId, UUID proofId);
}
