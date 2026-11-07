package com.legalsuite.service;

import com.legalsuite.common.TenantContext;
import com.legalsuite.domain.FirmReferral;
import com.legalsuite.domain.FirmReferralCode;
import com.legalsuite.domain.Tenant;
import com.legalsuite.repo.FirmReferralCodeRepository;
import com.legalsuite.repo.FirmReferralRepository;
import com.legalsuite.repo.TenantRepository;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralService {
    public static final String REWARD = "Reward to be confirmed. Owlaw does not pay a referral fee yet.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FirmReferralCodeRepository codes;
    private final FirmReferralRepository referrals;
    private final TenantRepository tenants;

    public ReferralService(FirmReferralCodeRepository codes, FirmReferralRepository referrals, TenantRepository tenants) {
        this.codes = codes;
        this.referrals = referrals;
        this.tenants = tenants;
    }

    @Transactional
    public void onRegistered(Tenant tenant, String rawCode) {
        ensureCode(tenant.getId(), tenant.getSlug());
        if (rawCode == null || rawCode.isBlank()) return;
        FirmReferralCode code = codes.findByCodeIgnoreCase(rawCode.trim()).orElse(null);
        if (code == null || code.getTenantId().equals(tenant.getId())) return;
        if (referrals.existsByReferredTenantId(tenant.getId())) return;
        FirmReferral row = new FirmReferral();
        row.setReferrerTenantId(code.getTenantId());
        row.setReferredTenantId(tenant.getId());
        row.setReferredFirmName(tenant.getFirmName());
        referrals.save(row);
    }

    @Transactional
    public Map<String, Object> mine() {
        UUID id = TenantContext.requireTenant();
        Tenant tenant = tenants.findById(id).orElseThrow();
        String code = ensureCode(id, tenant.getSlug());
        List<Map<String, Object>> firms = referrals.findByReferrerTenantIdOrderByCreatedAtDesc(id).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("firmName", row.getReferredFirmName());
            item.put("createdAt", row.getCreatedAt());
            return item;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("referredFirms", firms);
        out.put("reward", REWARD);
        return out;
    }

    private String ensureCode(UUID tenantId, String slug) {
        return codes.findById(tenantId).map(FirmReferralCode::getCode).orElseGet(() -> {
            FirmReferralCode row = new FirmReferralCode();
            row.setTenantId(tenantId);
            row.setCode(mint(slug));
            return codes.save(row).getCode();
        });
    }

    private String mint(String slug) {
        String base = (slug == null ? "FIRM" : slug).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (base.length() > 8) base = base.substring(0, 8);
        if (base.length() < 3) base = "FIRM";
        for (int i = 0; i < 8; i++) {
            String code = base + (1000 + RANDOM.nextInt(9000));
            if (codes.findByCodeIgnoreCase(code).isEmpty()) return code;
        }
        return base + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
