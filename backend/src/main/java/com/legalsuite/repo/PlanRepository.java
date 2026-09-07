package com.legalsuite.repo;

import com.legalsuite.domain.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findBySlug(String slug);
    List<Plan> findByActiveTrueOrderBySortOrderAsc();
}
