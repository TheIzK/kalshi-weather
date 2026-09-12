package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SignalRepository extends JpaRepository<Signal, UUID> {
    boolean existsByMarketIdAndStatusIn(String marketId, Collection<SignalStatus> statuses);

    List<Signal> findByComputedAtBetween(Instant start, Instant end);
}
