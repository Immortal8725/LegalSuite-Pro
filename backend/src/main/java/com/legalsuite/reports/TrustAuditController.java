package com.legalsuite.reports;

import com.legalsuite.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrustAuditController {
    private final TrustAuditService audit;

    public TrustAuditController(TrustAuditService audit) { this.audit = audit; }

    @GetMapping("/api/v1/trust/audit-pack")
    public ApiResponse<?> pack(@RequestParam LocalDate from, @RequestParam LocalDate to) { return ApiResponse.ok(audit.pack(from, to)); }

    @GetMapping("/api/v1/trust/audit-pack.csv")
    public ResponseEntity<byte[]> csv(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trust-audit-pack-" + from + "-" + to + ".csv")
                .body(audit.csv(from, to).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/v1/trust/audit-pack.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trust-audit-pack-" + from + "-" + to + ".pdf")
                .body(audit.pdf(from, to));
    }

    @GetMapping("/api/v1/trust/investments")
    public ApiResponse<?> investments() { return ApiResponse.ok(audit.listInvestments()); }

    @PostMapping("/api/v1/trust/investments")
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) { return ApiResponse.ok(audit.saveInvestment(null, body)); }

    @PutMapping("/api/v1/trust/investments/{id}")
    public ApiResponse<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(audit.saveInvestment(id, body)); }
}
