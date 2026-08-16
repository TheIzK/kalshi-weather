package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbProbablePitcher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MlbProbablePitcherRepository extends JpaRepository<MlbProbablePitcher, Long> {

    Optional<MlbProbablePitcher> findByGamePkAndTeamId(Long gamePk, Short teamId);
}
