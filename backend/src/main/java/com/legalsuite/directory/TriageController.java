package com.legalsuite.directory;

import com.legalsuite.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated and IP rate limited. The text is not stored or logged. */
@RestController
public class TriageController {
    private final TriageService triage;

    public TriageController(TriageService triage) {
        this.triage = triage;
    }

    @PostMapping("/api/v1/public/triage")
    public ApiResponse<?> triage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text"));
        String town = body.get("town") == null ? "" : String.valueOf(body.get("town"));
        String lang = body.get("lang") == null ? "en" : String.valueOf(body.get("lang"));
        return ApiResponse.ok(triage.triage(request.getRemoteAddr(), text, town, lang));
    }
}
