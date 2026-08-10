package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EnsembleForecastRepository extends JpaRepository<EnsembleForecast, UUID> {

    List<EnsembleForecast> findBySubjectKeyAndValidFor(String subjectKey, LocalDate validFor);
}
