package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActualObservationRepository extends JpaRepository<ActualObservation, UUID> {
    /** Bulk-fetched once per station per backfill run, rather than one exists-query per
     * settled market — a station can have hundreds of settled markets but only as many
     * distinct dates as days it's been running. */
    List<ActualObservation> findBySubjectKeyAndSource(String subjectKey, String source);
}
