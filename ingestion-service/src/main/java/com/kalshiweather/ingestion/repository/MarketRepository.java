package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MarketRepository extends JpaRepository<Market, String> {

    List<Market> findBySeriesTicker(String seriesTicker);

    /** Row = {seriesTicker, mostRecentUpdatedAt} — the dashboard's per-series "last
     * successful ingestion" health signal. */
    @Query("SELECT m.seriesTicker, MAX(m.updatedAt) FROM Market m GROUP BY m.seriesTicker")
    List<Object[]> findLastUpdatedPerSeries();
}
