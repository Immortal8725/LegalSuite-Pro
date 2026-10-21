package com.legalsuite.importer;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.Ids;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImportController {
    private final ImportService imports;

    public ImportController(ImportService imports) { this.imports = imports; }

    @GetMapping("/api/v1/import/kinds")
    public ApiResponse<?> kinds() { return ApiResponse.ok(imports.kinds()); }

    @GetMapping("/api/v1/import/templates/{kind}")
    public ResponseEntity<byte[]> template(@PathVariable String kind) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=owlaw-import-" + kind + ".csv")
                .body(imports.template(kind).getBytes(StandardCharsets.UTF_8));
    }

    /** Body: {"csv": "...", "dryRun": true, "trustAccountId": "..."}. */
    @PostMapping("/api/v1/import/{kind}")
    public ApiResponse<?> run(@PathVariable String kind, @RequestBody Map<String, Object> body) {
        boolean dry = !"false".equals(String.valueOf(body.getOrDefault("dryRun", "true")));
        return ApiResponse.ok(imports.run(kind, String.valueOf(body.getOrDefault("csv", "")), dry,
                Ids.optUuid(body.get("trustAccountId"), "trust account")));
    }
}
