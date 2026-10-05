package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.service.OutboundMessageService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboundController {
    private final OutboundMessageService outbound;

    public OutboundController(OutboundMessageService outbound) {
        this.outbound = outbound;
    }

    @GetMapping("/api/v1/outbound/readiness")
    public ApiResponse<?> readiness() {
        return ApiResponse.ok(outbound.readiness());
    }

    @GetMapping("/api/v1/outbound")
    public ApiResponse<?> list(@RequestParam(value = "caseId", required = false) String caseId) {
        return ApiResponse.ok(outbound.list(caseId));
    }

    @PostMapping("/api/v1/outbound/sms")
    public ApiResponse<?> sms(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(outbound.sendSms(body), "SMS sent");
    }

    @PostMapping("/api/v1/outbound/whatsapp")
    public ApiResponse<?> whatsapp(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(outbound.sendWhatsapp(body), "WhatsApp sent");
    }

    @PostMapping("/api/v1/outbound/email")
    public ApiResponse<?> email(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> sent = outbound.sendEmail(body);
        String notice = sent.get("notice") == null ? "Email sent" : String.valueOf(sent.get("notice"));
        return ApiResponse.ok(sent, notice);
    }

    @PostMapping("/api/v1/outbound/twilio/status/{token}")
    public ResponseEntity<Void> status(@PathVariable String token, HttpServletRequest request) {
        outbound.onCarrierStatus(token, request.getParameterMap(), request.getHeader("X-Twilio-Signature"));
        return ResponseEntity.noContent().build();
    }
}
