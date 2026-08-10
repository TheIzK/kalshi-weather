package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectStationRepository extends JpaRepository<SubjectStation, String> {
}
