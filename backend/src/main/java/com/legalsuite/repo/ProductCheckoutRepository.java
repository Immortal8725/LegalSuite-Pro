package com.legalsuite.repo;

import com.legalsuite.domain.ProductCheckout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCheckoutRepository extends JpaRepository<ProductCheckout, UUID> {
    Optional<ProductCheckout> findByMerchantPaymentId(String merchantPaymentId);
}
