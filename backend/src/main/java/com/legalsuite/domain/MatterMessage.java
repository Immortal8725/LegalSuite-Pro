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

/** One message in a matter's client inbox: between firm staff and the matter's client. */
@Entity
@Table(name = "matter_messages")
@Getter
@Setter
@NoArgsConstructor
public class MatterMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID caseId;
    private UUID clientId;
    /** staff, client or system */
    @Column(nullable = false, length = 20)
    private String senderType;
    private UUID senderId;
    private String senderName;
    /** message or stage_update */
    @Column(nullable = false, length = 30)
    private String kind = "message";
    @Column(length = 8000)
    private String body;
    private UUID documentId;
    private String attachmentName;
    private Long attachmentSize;
    private Instant readByClientAt;
    /** Earlier history kept from the client after a Change client. Staff still see it. */
    @Column(name = "hidden_from_client", nullable = false)
    private boolean hiddenFromClient;
    private Instant readByStaffAt;
    private UUID readByStaffId;
    private Instant createdAt = Instant.now();
}
