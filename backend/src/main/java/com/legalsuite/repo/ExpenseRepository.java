package com.legalsuite.repo;

import com.legalsuite.domain.Expense;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByTenantIdOrderByDateDesc(UUID tenantId);
}
