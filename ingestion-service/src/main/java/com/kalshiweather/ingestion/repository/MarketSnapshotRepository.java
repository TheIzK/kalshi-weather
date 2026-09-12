package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.MarketSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, UUID> {
}
