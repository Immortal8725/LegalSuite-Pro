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
@Table(name = "outbound_messages")
@Getter
@Setter
@NoArgsConstructor
public class OutboundMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    private UUID caseId;
    private UUID clientId;
    private UUID contactId;
    private UUID actorId;
    /** sms, whatsapp, or email. */
    @Column(nullable = false, length = 16)
    private String channel;
    /** queued, sent, delivered, failed, refused, or dry_run. */
    @Column(nullable = false, length = 32)
    private String status = "queued";
    @Column(length = 320)
    private String toAddress;
    @Column(length = 320)
    private String fromAddress;
    @Column(length = 200)
    private String subject;
    @Column(columnDefinition = "TEXT")
    private String body;
    /** twilio, smtp, or none. */
    @Column(length = 16)
    private String provider;
    /** Twilio message SID. Not a credential. */
    @Column(length = 64)
    private String providerSid;
    @Column(length = 500)
    private String errorMessage;
    private BigDecimal unitCost = BigDecimal.ZERO;
    /** Unguessable token on the Twilio status URL. Never returned to the staff UI. */
    @Column(unique = true, length = 64)
    private String callbackToken;
    private Instant createdAt = Instant.now();
}
