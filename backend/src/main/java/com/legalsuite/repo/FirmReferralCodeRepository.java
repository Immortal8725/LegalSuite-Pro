package com.legalsuite.repo;

import com.legalsuite.domain.FirmReferralCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmReferralCodeRepository extends JpaRepository<FirmReferralCode, UUID> {
    Optional<FirmReferralCode> findByCodeIgnoreCase(String code);
}
