package com.legalsuite.repo;

import com.legalsuite.domain.Conversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByTenantIdOrderByLastMessageAtDesc(UUID tenantId);
}
