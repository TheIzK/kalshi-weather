package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbTeamStandingsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MlbTeamStandingsSnapshotRepository extends JpaRepository<MlbTeamStandingsSnapshot, Long> {

    /** Most recent standings snapshot for a team, as of today or earlier. */
    Optional<MlbTeamStandingsSnapshot> findFirstByTeamIdOrderByAsOfDateDesc(Short teamId);
}
