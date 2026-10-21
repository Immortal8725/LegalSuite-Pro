package com.legalsuite.ops;

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
@Table(name = "ops_backup_runs")
@Getter
@Setter
@NoArgsConstructor
public class BackupRun {
    @Id
    private UUID id = UUID.randomUUID();
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private String status = "running";
    private Integer tablesCount;
    private Long rowsCount;
    private Long bytesCount;
    @Column(length = 64)
    private String sha256;
    @Column(length = 500)
    private String detail;
}
