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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PracticeReportsController {
    private final PracticeReportsService reports;

    public PracticeReportsController(PracticeReportsService reports) { this.reports = reports; }

    @GetMapping("/api/v1/reports/practice")
    public ApiResponse<?> dashboard() { return ApiResponse.ok(reports.dashboard()); }

    @GetMapping("/api/v1/exports/accounting.csv")
    public ResponseEntity<byte[]> accounting(@RequestParam String format, @RequestParam LocalDate from, @RequestParam LocalDate to,
                                             @RequestParam(required = false) String accountCode) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + format + "-invoices-" + from + "-" + to + ".csv")
                .body(reports.accountingCsv(format, from, to, accountCode).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/v1/cases/{id}/bundle/documents")
    public ApiResponse<?> candidates(@PathVariable UUID id) { return ApiResponse.ok(reports.bundleCandidates(id)); }

    @PostMapping("/api/v1/cases/{id}/bundle")
    public ResponseEntity<byte[]> bundle(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bundle.pdf")
                .body(reports.bundle(id, body));
    }
}
