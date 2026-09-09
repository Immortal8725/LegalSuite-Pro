package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String role = "associate";
    private String title;
    private String phone;
    private String barNumber;
    private String barState;
    private BigDecimal hourlyRate;
    @Column(length = 4000)
    private String bio;
    @Column(length = 2000)
    private String practiceAreasJson;
    private String status = "active";
    private String onlineStatus = "offline";
    private Instant lastLoginAt;
    private boolean emailVerified;
    private String totpSecret;
    private boolean totpEnabled;
    private Instant createdAt = Instant.now();

    public String getFullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    public String getInitials() {
        String a = firstName == null || firstName.isBlank() ? "U" : firstName.substring(0, 1);
        String b = lastName == null || lastName.isBlank() ? "" : lastName.substring(0, 1);
        return (a + b).toUpperCase();
    }
}
