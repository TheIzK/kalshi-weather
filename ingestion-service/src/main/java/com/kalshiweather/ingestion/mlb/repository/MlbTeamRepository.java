package com.kalshiweather.ingestion.mlb.repository;

import com.kalshiweather.ingestion.mlb.entity.MlbTeam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MlbTeamRepository extends JpaRepository<MlbTeam, Short> {
}
