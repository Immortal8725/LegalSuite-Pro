package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "firm_phone_numbers")
@Getter
@Setter
@NoArgsConstructor
public class FirmPhoneNumber {
    public static final String KIND_DID = "did";
    public static final String KIND_LANDLINE = "verified_landline";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RELEASED = "released";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 20)
    private String e164;
    /** did (rented local number) or verified_landline (outgoing caller ID). */
    @Column(nullable = false, length = 32)
    private String kind;
    @Column(nullable = false, length = 32)
    private String status = STATUS_PENDING;
    private String locality;
    private String region;
    @Column(length = 8)
    private String country;
    /** Twilio IncomingPhoneNumber or OutgoingCallerId SID. Not a credential. */
    @Column(length = 64)
    private String twilioSid;
    /** Short-lived code the attorney enters on the landline. Cleared once verified. */
    @Column(length = 12)
    private String validationCode;
    @Column(length = 80)
    private String friendlyName;
    private boolean defaultOutbound;
    private Instant createdAt = Instant.now();
    private Instant verifiedAt;
    private Instant updatedAt = Instant.now();
}
