package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.DocumentTemplate;
import com.legalsuite.domain.LegalCase;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.AppUserRepository;
import com.legalsuite.repo.ClientRepository;
import com.legalsuite.repo.DocumentTemplateRepository;
import com.legalsuite.repo.LegalCaseRepository;
import com.legalsuite.repo.TenantRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {
    private final DocumentTemplateRepository templates;
    private final LegalCaseRepository cases;
    private final ClientRepository clients;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final AuditService audit;

    public TemplateService(
            DocumentTemplateRepository templates,
            LegalCaseRepository cases,
            ClientRepository clients,
            TenantRepository tenants,
            AppUserRepository users,
            AuditService audit) {
        this.templates = templates;
        this.cases = cases;
        this.clients = clients;
        this.tenants = tenants;
        this.users = users;
        this.audit = audit;
    }

    public List<Map<String, Object>> list() {
        return templates.findByTenantIdOrderByNameAsc(tid()).stream().map(this::view).toList();
    }

    public Map<String, Object> get(UUID id) {
        return view(require(id));
    }

    @Transactional
    public Map<String, Object> save(UUID id, Map<String, Object> body) {
        DocumentTemplate t = id == null
                ? new DocumentTemplate()
                : require(id);
        t.setTenantId(tid());
        t.setName(str(body, "name", t.getName() == null ? "Untitled template" : t.getName()));
        t.setCategory(str(body, "category", t.getCategory() == null ? "correspondence" : t.getCategory()));
        t.setBody(str(body, "body", t.getBody() == null ? "" : t.getBody()));
        templates.save(t);
        audit.record(id == null ? "template.create" : "template.update", "template", t.getId().toString(), t.getName());
        return view(t);
    }

    public Map<String, Object> merge(UUID id, Map<String, Object> body) {
        DocumentTemplate t = require(id);
        Map<String, String> values = context(body);
        String merged = MergeEngine.merge(t.getBody(), values);
        audit.record("template.merge", "template", t.getId().toString(), t.getName());
        Map<String, Object> out = view(t);
        out.put("merged", merged);
        out.put("values", values);
        return out;
    }

    private Map<String, String> context(Map<String, Object> body) {
        Map<String, String> values = new HashMap<>();
        Tenant tenant = tenants.findById(tid()).orElseThrow(() -> ApiException.notFound("Firm not found"));
        values.put("firm.name", tenant.getFirmName());
        values.put("firm.phone", nz(tenant.getPhone()));
        values.put("firm.email", nz(tenant.getEmail()));
        values.put("firm.address", String.join(", ",
                List.of(nz(tenant.getAddressLine1()), nz(tenant.getCity()), nz(tenant.getState()), nz(tenant.getZip()))
                        .stream().filter(s -> !s.isBlank()).toList()));
        values.put("today", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        users.findById(TenantContext.requireUser()).ifPresent(u -> {
            values.put("attorney.name", (nz(u.getFirstName()) + " " + nz(u.getLastName())).trim());
            values.put("attorney.email", nz(u.getEmail()));
            values.put("attorney.title", nz(u.getTitle()));
        });
        if (body.get("caseId") != null) {
            LegalCase c = cases.findByIdAndTenantId(UUID.fromString(String.valueOf(body.get("caseId"))), tid())
                    .orElseThrow(() -> ApiException.notFound("Matter not found"));
            values.put("case.title", nz(c.getTitle()));
            values.put("case.number", nz(c.getCaseNumber()));
            values.put("case.status", nz(c.getStatus()));
            values.put("case.court", nz(c.getCourtName()));
            values.put("case.opposing", nz(c.getOpposingParty()));
            values.put("case.area", nz(c.getPracticeArea()));
            if (c.getClientId() != null) {
                clients.findByIdAndTenantId(c.getClientId(), tid()).ifPresent(cl -> fillClient(values, cl));
            }
        }
        if (body.get("clientId") != null) {
            Client cl = clients.findByIdAndTenantId(UUID.fromString(String.valueOf(body.get("clientId"))), tid())
                    .orElseThrow(() -> ApiException.notFound("Client not found"));
            fillClient(values, cl);
        }
        return values;
    }

    private void fillClient(Map<String, String> values, Client cl) {
        values.put("client.name", cl.displayName());
        values.put("client.email", nz(cl.getEmail()));
        values.put("client.phone", nz(cl.getPhone()));
        values.put("client.city", nz(cl.getCity()));
        values.put("client.state", nz(cl.getState()));
    }

    private DocumentTemplate require(UUID id) {
        return templates.findByIdAndTenantId(id, tid()).orElseThrow(() -> ApiException.notFound("Template not found"));
    }

    private Map<String, Object> view(DocumentTemplate t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("category", t.getCategory());
        m.put("body", t.getBody());
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    private UUID tid() {
        return TenantContext.requireTenant();
    }

    private static String str(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
