package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.ReferralService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReferralController {
    private final ReferralService referrals;

    public ReferralController(ReferralService referrals) {
        this.referrals = referrals;
    }

    @GetMapping("/api/v1/referrals")
    public ApiResponse<?> mine() {
        return ApiResponse.ok(referrals.mine());
    }
}
