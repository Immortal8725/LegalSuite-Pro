package com.legalsuite.web;

import com.legalsuite.common.ApiException;
import com.legalsuite.common.ApiResponse;
import com.legalsuite.payfast.MinutesChargeJob;
import com.legalsuite.payfast.PayFastCheckoutService;
import com.legalsuite.payfast.PayFastItnService;
import com.legalsuite.payfast.PayFastSignature;
import com.legalsuite.payfast.ProductBillingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductBillingController {
    private final ProductBillingService billing;
    private final PayFastCheckoutService checkout;
    private final MinutesChargeJob minutes;
    private final PayFastItnService itn;

    public ProductBillingController(
            ProductBillingService billing,
            PayFastCheckoutService checkout,
            MinutesChargeJob minutes,
            PayFastItnService itn) {
        this.billing = billing;
        this.checkout = checkout;
        this.minutes = minutes;
        this.itn = itn;
    }

    @GetMapping("/api/v1/product-billing")
    public ApiResponse<?> status() {
        return ApiResponse.ok(billing.status());
    }

    @PostMapping("/api/v1/product-billing/seats/checkout")
    public ApiResponse<?> seatCheckout(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(checkout.seatCheckout(seats(body)));
    }

    @PostMapping("/api/v1/product-billing/token/checkout")
    public ApiResponse<?> tokenCheckout() {
        return ApiResponse.ok(checkout.tokenCheckout());
    }

    @PostMapping("/api/v1/product-billing/minutes/charge")
    public ApiResponse<?> chargeMinutes(@RequestBody(required = false) Map<String, Object> body) {
        String period = body == null || body.get("period") == null ? null : String.valueOf(body.get("period"));
        return ApiResponse.ok(minutes.charge(period));
    }

    @PostMapping("/api/v1/product-billing/payfast/itn")
    public ResponseEntity<String> itn(HttpServletRequest request) {
        List<PayFastSignature.Field> fields = new ArrayList<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getParameter(name);
            fields.add(new PayFastSignature.Field(name, value == null ? "" : value));
        }
        PayFastItnService.ItnOutcome outcome = itn.handle(fields);
        if (outcome.httpStatus() == 200) {
            return ResponseEntity.ok("OK");
        }
        return ResponseEntity.badRequest().body(outcome.reason());
    }

    private static int seats(Map<String, Object> body) {
        if (body == null || body.get("seats") == null || String.valueOf(body.get("seats")).isBlank()) return 1;
        try {
            return Integer.parseInt(String.valueOf(body.get("seats")).trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("Seat count must be a whole number");
        }
    }
}
