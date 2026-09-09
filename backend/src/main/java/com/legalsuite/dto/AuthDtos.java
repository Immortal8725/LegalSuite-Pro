package com.legalsuite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AuthDtos {
    public record LoginRequest(
            @NotBlank String firmSlug,
            @NotBlank @Email String email,
            @NotBlank String password,
            String totpCode) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 255) String firmName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String phone,
            String firmSlug,
            String planSlug,
            String firmSize,
            String country,
            String state,
            String city,
            List<String> practiceAreas) {}

    public record RefreshRequest(String refreshToken) {}

    public record PortalLoginRequest(
            @NotBlank String firmSlug,
            @NotBlank @Email String email,
            @NotBlank String password) {}
}
