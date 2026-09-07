package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID tenantId;
    private String type = "individual";
    private String status = "active";
    private String firstName;
    private String lastName;
    private String companyName;
    private String email;
    private String phone;
    private String mobile;
    private String addressLine1;
    private String city;
    private String state;
    private String zip;
    private boolean portalEnabled;
    private String portalPasswordHash;
    private String source;
    private UUID assignedAttorneyId;
    @Column(length = 2000)
    private String tagsJson;
    @Column(length = 4000)
    private String notes;
    private BigDecimal defaultHourlyRate;
    private LocalDate dateOfBirth;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String displayName() {
        if (companyName != null && !companyName.isBlank()) {
            return companyName;
        }
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
