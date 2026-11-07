package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One invite code per firm. There is no payout attached to it. */
@Entity
@Table(name = "firm_referral_codes")
@Getter
@Setter
@NoArgsConstructor
public class FirmReferralCode {
    @Id
    private UUID tenantId;
    @Column(nullable = false, unique = true, length = 32)
    private String code;
}
