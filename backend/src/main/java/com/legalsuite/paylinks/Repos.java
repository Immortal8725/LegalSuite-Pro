package com.legalsuite.paylinks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FirmPaymentSettingsRepository extends JpaRepository<FirmPaymentSettings, UUID> {}

interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {
    Optional<PaymentLink> findByToken(String token);
    List<PaymentLink> findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(UUID tenantId, UUID invoiceId);
}
