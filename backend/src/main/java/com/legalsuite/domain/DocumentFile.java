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
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class DocumentFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID clientId;
    private UUID uploadedBy;
    private String name;
    private String originalName;
    private String category;
    private String mimeType;
    private long sizeBytes;
    private String storagePath;
    @Column(length = 2000)
    private String tagsJson;
    private boolean privileged;
    private Instant createdAt = Instant.now();
}
