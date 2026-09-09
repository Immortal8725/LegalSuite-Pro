package com.legalsuite.web;

import com.legalsuite.common.ApiResponse;
import com.legalsuite.common.TenantContext;
import com.legalsuite.dto.AuthDtos.LoginRequest;
import com.legalsuite.dto.AuthDtos.PortalLoginRequest;
import com.legalsuite.dto.AuthDtos.RefreshRequest;
import com.legalsuite.dto.AuthDtos.RegisterRequest;
import com.legalsuite.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, Object> data = auth.login(req);
        if (Boolean.TRUE.equals(data.get("requiresTotp"))) {
            return ApiResponse.ok(data, "Authenticator code required");
        }
        return ApiResponse.ok(data, "Login successful");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(auth.register(req), "Registration successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshRequest req) {
        return ApiResponse.ok(auth.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        auth.logout(TenantContext.requireUser());
        return ApiResponse.ok(null, "Logged out");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(auth.me());
    }

    @PostMapping("/totp/start")
    public ApiResponse<Map<String, Object>> totpStart() {
        return ApiResponse.ok(auth.totpStart(), "Add this secret to your authenticator app");
    }

    @PostMapping("/totp/confirm")
    public ApiResponse<Map<String, Object>> totpConfirm(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(auth.totpConfirm(body), "Authenticator enabled");
    }

    @PostMapping("/totp/disable")
    public ApiResponse<Map<String, Object>> totpDisable(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(auth.totpDisable(body == null ? Map.of() : body), "Authenticator disabled");
    }

    @GetMapping("/check-slug/{slug}")
    public ApiResponse<Map<String, Boolean>> checkSlug(@PathVariable String slug) {
        return ApiResponse.ok(Map.of("available", auth.slugAvailable(slug)));
    }
}

@RestController
@RequestMapping("/api/v1/portal")
class PortalController {
    private final AuthService auth;
    private final com.legalsuite.service.CommsService comms;

    PortalController(AuthService auth, com.legalsuite.service.CommsService comms) {
        this.auth = auth;
        this.comms = comms;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody PortalLoginRequest req) {
        return ApiResponse.ok(auth.portalLogin(req));
    }
}
