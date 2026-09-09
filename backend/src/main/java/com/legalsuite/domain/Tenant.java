package com.legalsuite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String firmName;
    @Column(unique = true, nullable = false)
    private String slug;
    private String customDomain;
    private String logoUrl;
    private UUID planId;
    private String status = "trial";
    private Instant trialEndsAt;
    @Column(length = 4000)
    private String settingsJson;
    private String addressLine1;
    private String city;
    private String state;
    private String zip;
    private String country = "US";
    private String phone;
    private String email;
    private String website;
    @Column(length = 2000)
    private String practiceAreasJson;
    private String tagline;
    private boolean onboardingCompleted;
    private String ffcNumber;
    private LocalDate ffcExpiresOn;
    private String ffcHolderName;
    private Instant bankFeedImportedAt;
    private String lastBankFeedSource;
    private String informationOfficerName;
    private String informationOfficerEmail;
    @Column(length = 8000)
    private String paiaManualBody;
    private boolean popiaOperatorAcknowledged;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
