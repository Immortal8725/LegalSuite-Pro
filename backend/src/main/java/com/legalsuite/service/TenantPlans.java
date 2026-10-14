package com.legalsuite.service;

import com.legalsuite.domain.Plan;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.PlanRepository;
import com.legalsuite.repo.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Which Owlaw plan a tenant is on. The old Light plan reads as Firm. */
@Service
public class TenantPlans {
    private final TenantRepository tenants;
    private final PlanRepository plans;

    public TenantPlans(TenantRepository tenants, PlanRepository plans) {
        this.tenants = tenants;
        this.plans = plans;
    }

    public Pricing.PlanSpec planFor(UUID tenantId) {
        if (tenantId == null) return Pricing.FIRM;
        String slug = tenants.findById(tenantId)
                .map(Tenant::getPlanId)
                .flatMap(id -> id == null ? java.util.Optional.<Plan>empty() : plans.findById(id))
                .map(Plan::getSlug)
                .orElse(Pricing.DEFAULT_SLUG);
        return Pricing.plan(slug);
    }
}
