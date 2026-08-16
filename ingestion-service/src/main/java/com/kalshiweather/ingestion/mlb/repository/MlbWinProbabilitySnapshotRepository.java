package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbWinProbabilitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MlbWinProbabilitySnapshotRepository extends JpaRepository<MlbWinProbabilitySnapshot, Long> {
}
