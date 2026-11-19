package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.BucketCalibration;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BucketCalibrationRepository extends JpaRepository<BucketCalibration, UUID> {
    /** Spring Data translates a null strikeType parameter to "strike_type IS NULL" (not
     * "= NULL", which would never match) — relied on here for non-weather domains, which
     * have no strike shape. */
    Optional<BucketCalibration> findByDomainAndStrikeTypeAndDirection(String domain, StrikeType strikeType, SignalDirection direction);
}
