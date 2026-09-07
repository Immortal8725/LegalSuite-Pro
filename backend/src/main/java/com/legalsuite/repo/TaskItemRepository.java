package com.legalsuite.repo;

import com.legalsuite.domain.TaskItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskItemRepository extends JpaRepository<TaskItem, UUID> {
    List<TaskItem> findByTenantIdOrderByDueDateAsc(UUID tenantId);
    Optional<TaskItem> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantIdAndStatusNot(UUID tenantId, String status);
}
