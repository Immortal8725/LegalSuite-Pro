package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "call_records")
@Getter
@Setter
@NoArgsConstructor
public class CallRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID clientId;
    private UUID callerUserId;
    private UUID calleeUserId;
    private String callType = "webrtc";
    private String direction = "internal";
    private String status = "initiated";
    private Instant startedAt = Instant.now();
    private Instant answeredAt;
    private Instant endedAt;
    private int durationSeconds;
    private BigDecimal costPerMinute = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private boolean recordingEnabled;
    private boolean recordingConsentGiven;
    private String recordingUrl;
    @Column(length = 8000)
    private String transcript;
    @Column(length = 4000)
    private String notes;
    private boolean billable = true;
    private UUID timeEntryId;
    /** Caller ID presented to the other party (firm DID or verified landline). */
    @Column(length = 20)
    private String fromNumber;
    /** Destination on the public network. */
    @Column(length = 20)
    private String toNumber;
    /** Attorney handset that rings first on a callback bridge. */
    @Column(length = 20)
    private String staffCallbackNumber;
    /** did or verified_landline. */
    @Column(length = 32)
    private String callerIdKind;
    private UUID firmPhoneNumberId;
    @Column(length = 64)
    private String twilioCallSid;
    /** Unguessable token embedded in Twilio callback URLs. Never returned to the staff UI. */
    @Column(unique = true, length = 64)
    private String bridgeToken;
    /** Staff explicitly confirmed the call is not tied to a matter. */
    private boolean nonMatter;
}
