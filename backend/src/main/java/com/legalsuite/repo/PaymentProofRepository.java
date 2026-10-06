package com.legalsuite.repo;

import com.legalsuite.domain.PaymentProof;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> {
    List<PaymentProof> findByTenantIdAndInvoiceIdOrderBySubmittedAtDesc(UUID tenantId, UUID invoiceId);
    List<PaymentProof> findByTenantIdOrderBySubmittedAtDesc(UUID tenantId);
    List<PaymentProof> findByTenantIdAndStatusOrderBySubmittedAtAsc(UUID tenantId, String status);
    Optional<PaymentProof> findByIdAndTenantId(UUID id, UUID tenantId);
}
