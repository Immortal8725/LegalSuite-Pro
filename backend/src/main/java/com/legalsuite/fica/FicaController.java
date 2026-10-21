package com.legalsuite.fica;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.TenantContext;
import com.legalsuite.service.PracticeService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FicaController {
    private final FicaService fica;
    private final PracticeService practice;

    public FicaController(FicaService fica, PracticeService practice) {
        this.fica = fica;
        this.practice = practice;
    }

    @GetMapping("/api/v1/fica/factors")
    public ApiResponse<?> factors() { return ApiResponse.ok(fica.factors()); }

    @GetMapping("/api/v1/fica/report")
    public ApiResponse<?> report() { return ApiResponse.ok(fica.report()); }

    @GetMapping("/api/v1/fica/report.csv")
    public ResponseEntity<byte[]> reportCsv() {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fica-register.csv")
                .body(fica.reportCsv().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/api/v1/fica/rmcp.docx")
    public ResponseEntity<byte[]> rmcp() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=RMCP-template.docx")
                .body(fica.rmcpDocx());
    }

    @PostMapping("/api/v1/fica/reminders/run")
    public ApiResponse<?> remind() { return ApiResponse.ok(Map.of("created", fica.remind(TenantContext.requireTenant()))); }

    @GetMapping("/api/v1/fica/clients/{clientId}")
    public ApiResponse<?> get(@PathVariable UUID clientId) { return ApiResponse.ok(fica.get(clientId)); }

    @PutMapping("/api/v1/fica/clients/{clientId}")
    public ApiResponse<?> save(@PathVariable UUID clientId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(fica.save(clientId, body));
    }

    @PostMapping("/api/v1/fica/clients/{clientId}/verify")
    public ApiResponse<?> verify(@PathVariable UUID clientId) { return ApiResponse.ok(fica.verify(clientId)); }

    /** kind = id or poa. Stores the file in the firm's documents (category fica_id / fica_poa) and links it. */
    @PostMapping(value = "/api/v1/fica/clients/{clientId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable UUID clientId, @RequestParam("kind") String kind,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        fica.get(clientId);
        String k = "poa".equals(kind) ? "poa" : "id";
        Map<String, Object> doc = practice.upload(file, null, clientId, "fica_" + k);
        return ApiResponse.ok(fica.save(clientId, Map.of(k.equals("id") ? "idDocumentId" : "poaDocumentId", String.valueOf(doc.get("id")))));
    }
}
