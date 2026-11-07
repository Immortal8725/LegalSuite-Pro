package com.legalsuite.service;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.Roles;
import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.AppUser;
import com.legalsuite.repo.AppUserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Plan limits from Pricing.java, applied on the server. The screen shows the same facts. */
@Service
public class PlanAccess {
    private final TenantPlans plans;
    private final AppUserRepository users;

    public PlanAccess(TenantPlans plans, AppUserRepository users) {
        this.plans = plans;
        this.users = users;
    }

    public Pricing.PlanSpec current() {
        return plans.planFor(TenantContext.getTenantId());
    }

    public void requireRecording() {
        if (!current().callRecording()) {
            throw ApiException.forbidden("Call recording is included on the Practice plan.");
        }
    }

    public void requireWebsite() {
        if (!current().websiteIncluded()) {
            throw ApiException.forbidden("The public website is included on Firm and Practice. On Solo it is an add-on.");
        }
    }

    public void requireInvite(String role) {
        Pricing.PlanSpec plan = current();
        boolean attorney = Roles.rank(role) >= Roles.rank("associate");
        if (!attorney && !plan.staffSeats()) {
            throw ApiException.badRequest("Staff seats are included on Firm and Practice. Solo is one attorney.");
        }
        if (attorney && plan.maxAttorneys() != null && attorneyCount() >= plan.maxAttorneys()) {
            throw ApiException.badRequest("Solo includes one attorney. Firm and Practice add more attorneys.");
        }
    }

    /** Assistant actions for this month: attorneys at the plan rate, plus 50 for each staff seat. */
    public int assistantAllowance() {
        Pricing.PlanSpec plan = current();
        int attorneys = 0;
        int staff = 0;
        UUID tid = TenantContext.requireTenant();
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(tid)) {
            if (u.getStatus() != null && "inactive".equalsIgnoreCase(u.getStatus())) continue;
            if (Roles.rank(u.getRole()) >= Roles.rank("associate")) attorneys++;
            else staff++;
        }
        if (attorneys < 1) attorneys = 1;
        return attorneys * plan.aiActionsPerAttorney() + staff * Pricing.STAFF_AI_ACTIONS;
    }

    private int attorneyCount() {
        UUID tid = TenantContext.requireTenant();
        int n = 0;
        for (AppUser u : users.findByTenantIdOrderByLastNameAsc(tid)) {
            if (u.getStatus() != null && "inactive".equalsIgnoreCase(u.getStatus())) continue;
            if (Roles.rank(u.getRole()) >= Roles.rank("associate")) n++;
        }
        return n;
    }
}
