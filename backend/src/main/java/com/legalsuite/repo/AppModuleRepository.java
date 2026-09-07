package com.legalsuite.repo;

import com.legalsuite.domain.AppModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppModuleRepository extends JpaRepository<AppModule, UUID> {
    Optional<AppModule> findBySlug(String slug);
    List<AppModule> findByActiveTrueOrderBySortOrderAsc();
}
