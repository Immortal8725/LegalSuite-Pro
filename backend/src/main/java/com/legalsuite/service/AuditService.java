package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AuditLog;
import com.legalsuite.repo.AuditLogRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String detail) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setActorId(TenantContext.getUserId());
        log.setActorEmail(TenantContext.getEmail());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail);
        logs.save(log);
    }

    public List<Map<String, Object>> list() {
        return logs.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenant(), PageRequest.of(0, 200))
                .stream()
                .map(this::view)
                .toList();
    }

    private Map<String, Object> view(AuditLog log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("action", log.getAction());
        m.put("entityType", log.getEntityType());
        m.put("entityId", log.getEntityId());
        m.put("detail", log.getDetail());
        m.put("actorEmail", log.getActorEmail());
        m.put("actorId", log.getActorId());
        m.put("createdAt", log.getCreatedAt());
        return m;
    }
}
