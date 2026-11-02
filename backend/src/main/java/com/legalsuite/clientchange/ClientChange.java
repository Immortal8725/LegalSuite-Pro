package com.legalsuite.clientchange;

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

/** One Change client on a matter, or one merge of a duplicate client. Never edited after it is written. */
@Entity
@Table(name = "client_changes")
@Getter
@Setter
@NoArgsConstructor
public class ClientChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 20)
    private String kind;
    private UUID caseId;
    @Column(nullable = false)
    private UUID fromClientId;
    @Column(nullable = false)
    private UUID toClientId;
    @Column(nullable = false, length = 1000)
    private String reason;
    private UUID actorId;
    @Column(nullable = false)
    private boolean showHistory;
    @Column(nullable = false)
    private boolean popiaFlag;
    @Column(length = 1000)
    private String popiaDetail;
    @Column(length = 4000)
    private String countsJson;
    private Instant createdAt = Instant.now();
}
