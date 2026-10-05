package com.legalsuite.repo;

import com.legalsuite.domain.ProductPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPaymentRepository extends JpaRepository<ProductPayment, UUID> {
    Optional<ProductPayment> findByPfPaymentId(String pfPaymentId);
    List<ProductPayment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
