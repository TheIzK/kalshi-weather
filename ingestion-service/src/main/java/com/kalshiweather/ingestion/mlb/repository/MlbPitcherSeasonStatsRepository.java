package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbPitcherSeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MlbPitcherSeasonStatsRepository extends JpaRepository<MlbPitcherSeasonStats, Long> {

    /** Most recent snapshot for a pitcher this season, as of today or earlier. */
    Optional<MlbPitcherSeasonStats> findFirstByPlayerIdAndSeasonOrderByAsOfDateDesc(Integer playerId, Integer season);
}
