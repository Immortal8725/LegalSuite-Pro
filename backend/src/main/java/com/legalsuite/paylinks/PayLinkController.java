package com.legalsuite.paylinks;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.payfast.PayFastSignature.Field;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayLinkController {
    private final PayLinkService pay;

    public PayLinkController(PayLinkService pay) { this.pay = pay; }

    @GetMapping("/api/v1/payments/settings")
    public ApiResponse<?> settings() { return ApiResponse.ok(pay.getSettings()); }

    @PutMapping("/api/v1/payments/settings")
    public ApiResponse<?> save(@RequestBody Map<String, Object> body) { return ApiResponse.ok(pay.saveSettings(body)); }

    @PostMapping("/api/v1/invoices/{id}/payment-link")
    public ApiResponse<?> create(@PathVariable UUID id) { return ApiResponse.ok(pay.create(id)); }

    @GetMapping("/api/v1/clients/{id}/statement")
    public ApiResponse<?> statement(@PathVariable UUID id) { return ApiResponse.ok(pay.statement(id)); }

    @GetMapping("/api/v1/clients/{id}/statement.pdf")
    public ResponseEntity<byte[]> statementPdf(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=statement.pdf").body(pay.statementPdf(id));
    }

    @GetMapping("/api/v1/public/pay/{token}")
    public ApiResponse<?> view(@PathVariable String token) { return ApiResponse.ok(pay.publicView(token)); }

    /** PayFast posts form-encoded. Field order matters for the signature, so read the raw body. */
    @PostMapping("/api/v1/public/pay/{token}/itn")
    public ResponseEntity<Void> itn(@PathVariable String token, HttpServletRequest req) throws IOException {
        String raw = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        List<Field> fields = new ArrayList<>();
        if (raw.isBlank()) {
            req.getParameterMap().forEach((k, v) -> fields.add(new Field(k, v.length == 0 ? "" : v[0])));
        } else {
            for (String pair : raw.split("&")) {
                if (pair.isEmpty()) continue;
                int i = pair.indexOf('=');
                String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
                String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                fields.add(new Field(k, v));
            }
        }
        pay.itn(token, fields);
        return ResponseEntity.ok().build();
    }
}
