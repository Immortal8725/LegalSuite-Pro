package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.JsonLists;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppModule;
import com.legalsuite.domain.CallRecord;
import com.legalsuite.domain.TenantModule;
import com.legalsuite.domain.UsageInvoice;
import com.legalsuite.repo.AppModuleRepository;
import com.legalsuite.repo.CallRecordRepository;
import com.legalsuite.repo.TenantModuleRepository;
import com.legalsuite.repo.UsageInvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {
    private final UsageInvoiceRepository invoices;
    private final TenantModuleRepository tenantModules;
    private final AppModuleRepository modules;
    private final CallRecordRepository calls;
    private final AuditService audit;

    public UsageService(
            UsageInvoiceRepository invoices,
            TenantModuleRepository tenantModules,
            AppModuleRepository modules,
            CallRecordRepository calls,
            AuditService audit) {
        this.invoices = invoices;
        this.tenantModules = tenantModules;
        this.modules = modules;
        this.calls = calls;
        this.audit = audit;
    }

    public Map<String, Object> preview() {
        return preview(YearMonth.now());
    }

    public Map<String, Object> preview(YearMonth month) {
        UUID tid = TenantContext.requireTenant();
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal modulesTotal = BigDecimal.ZERO;
        for (TenantModule tm : tenantModules.findByTenantId(tid)) {
            if (!tm.isEnabled()) continue;
            AppModule mod = modules.findById(tm.getModuleId()).orElse(null);
            if (mod == null || mod.isCore()) continue;
            BigDecimal price = mod.getPriceMonthly() == null ? BigDecimal.ZERO : mod.getPriceMonthly();
            if (price.signum() <= 0) continue;
            modulesTotal = modulesTotal.add(price);
            lines.add(Map.of(
                    "kind", "module",
                    "description", mod.getName() + " add-on",
                    "amount", price));
        }
        BigDecimal pstn = BigDecimal.ZERO;
        var from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int pstnMinutes = 0;
        for (CallRecord c : calls.findByTenantIdOrderByStartedAtDesc(tid)) {
            if (c.getStartedAt() == null || c.getStartedAt().isBefore(from) || !c.getStartedAt().isBefore(to)) continue;
            if (c.getCallType() == null || !c.getCallType().startsWith("pstn")) continue;
            pstn = pstn.add(c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost());
            pstnMinutes += Math.max(0, c.getDurationSeconds()) / 60;
        }
        if (pstn.signum() > 0 || pstnMinutes > 0) {
            lines.add(Map.of(
                    "kind", "pstn",
                    "description", "PSTN minutes (" + pstnMinutes + " min)",
                    "amount", pstn));
        }
        BigDecimal total = modulesTotal.add(pstn);
        String period = month.toString();
        UsageInvoice existing = invoices.findByTenantIdAndPeriod(tid, period).orElse(null);
        Map<String, Object> m = new HashMap<>();
        m.put("period", period);
        m.put("modulesSubtotal", modulesTotal);
        m.put("pstnSubtotal", pstn);
        m.put("pstnMinutes", pstnMinutes);
        m.put("total", total);
        m.put("lineItems", lines);
        m.put("note", "In-app WebRTC is $0. You pay add-on modules and public-network minutes at month end — no prepaid bucket.");
        m.put("issued", existing == null ? null : view(existing));
        return m;
    }

    public List<Map<String, Object>> history() {
        return invoices.findByTenantIdOrderByPeriodDesc(TenantContext.requireTenant()).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> issue() {
        YearMonth month = YearMonth.now();
        UUID tid = TenantContext.requireTenant();
        String period = month.toString();
        UsageInvoice existing = invoices.findByTenantIdAndPeriod(tid, period).orElse(null);
        if (existing != null) return view(existing);
        Map<String, Object> snap = preview(month);
        UsageInvoice inv = new UsageInvoice();
        inv.setTenantId(tid);
        inv.setPeriod(period);
        inv.setInvoiceNumber("USG-" + period.replace("-", ""));
        inv.setStatus("issued");
        inv.setDateIssued(LocalDate.now());
        inv.setModulesSubtotal(new BigDecimal(String.valueOf(snap.get("modulesSubtotal"))));
        inv.setPstnSubtotal(new BigDecimal(String.valueOf(snap.get("pstnSubtotal"))));
        inv.setTotal(new BigDecimal(String.valueOf(snap.get("total"))));
        inv.setLineItemsJson(JsonLists.toJson(snap.get("lineItems")));
        invoices.save(inv);
        audit.record("usage.issue", "usage", inv.getId().toString(), period + " " + inv.getTotal());
        return view(inv);
    }

    @Transactional
    public Map<String, Object> pay(UUID id) {
        UsageInvoice inv = invoices.findById(id)
                .filter(u -> u.getTenantId().equals(TenantContext.requireTenant()))
                .orElseThrow(() -> ApiException.notFound("Usage invoice not found"));
        inv.setStatus("paid");
        invoices.save(inv);
        audit.record("usage.pay", "usage", inv.getId().toString(), inv.getPeriod());
        return view(inv);
    }

    private Map<String, Object> view(UsageInvoice inv) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", inv.getId());
        m.put("period", inv.getPeriod());
        m.put("invoiceNumber", inv.getInvoiceNumber());
        m.put("status", inv.getStatus());
        m.put("dateIssued", inv.getDateIssued());
        m.put("modulesSubtotal", inv.getModulesSubtotal());
        m.put("pstnSubtotal", inv.getPstnSubtotal());
        m.put("total", inv.getTotal());
        m.put("lineItems", JsonLists.objects(inv.getLineItemsJson()));
        return m;
    }
}
