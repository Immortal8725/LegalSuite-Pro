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
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class TaskItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private UUID caseId;
    private UUID createdBy;
    private UUID assignedTo;
    private String title;
    @Column(length = 4000)
    private String description;
    private String status = "todo";
    private String priority = "medium";
    private Instant dueDate;
    private Instant completedAt;
    @Column(length = 4000)
    private String checklistJson;
    private Instant createdAt = Instant.now();
}
