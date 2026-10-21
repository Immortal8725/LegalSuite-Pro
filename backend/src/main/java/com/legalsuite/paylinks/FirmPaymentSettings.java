package com.legalsuite.paylinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "firm_payment_settings")
@Getter
@Setter
@NoArgsConstructor
public class FirmPaymentSettings {
    @Id
    private UUID tenantId;
    private boolean enabled = true;
    /** sandbox or live */
    private String mode = "sandbox";
    private String merchantId;
    @Column(length = 500)
    private String merchantKeyEnc;
    @Column(length = 500)
    private String passphraseEnc;
    private Instant updatedAt = Instant.now();
}
