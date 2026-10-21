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
    private final com.legalsuite.platform.PlatformActionRepository platformActions;

    public AuditService(AuditLogRepository logs, com.legalsuite.platform.PlatformActionRepository platformActions) {
        this.logs = logs;
        this.platformActions = platformActions;
    }

    /**
     * A platform (superadmin) action. Always lands in the platform log; when it concerns a firm it is also written to that
     * firm's own audit log so the firm can see what Owlaw staff did.
     */
    @Transactional
    public void platform(UUID tenantId, String action, String entityType, String entityId, String detail) {
        com.legalsuite.platform.PlatformAction a = new com.legalsuite.platform.PlatformAction();
        a.setTenantId(tenantId);
        a.setActorId(TenantContext.getUserId());
        a.setActorEmail(TenantContext.getEmail());
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setDetail(detail == null ? null : (detail.length() > 1000 ? detail.substring(0, 1000) : detail));
        a.setCreatedAt(java.time.Instant.now());
        platformActions.save(a);
        if (tenantId != null) firmLog(tenantId, action, entityType, entityId, detail);
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String detail) {
        recordFor(TenantContext.getTenantId(), action, entityType, entityId, detail);
    }

    /** Write to a named firm's log, for example when the platform operator approves that firm's site. */
    public void recordFor(UUID tenantId, String action, String entityType, String entityId, String detail) {
        if (tenantId == null) return;
        if (com.legalsuite.platform.PlatformGuard.isPlatformRole(TenantContext.getRole()) && !tenantId.equals(TenantContext.getTenantId())) {
            // An operator acting on a firm: keep it in the platform log as well as the firm's.
            platform(tenantId, action, entityType, entityId, detail);
            return;
        }
        firmLog(tenantId, action, entityType, entityId, detail);
    }

    private void firmLog(UUID tenantId, String action, String entityType, String entityId, String detail) {
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
