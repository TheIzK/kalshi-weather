package com.kalshiweather.ingestion.repository;

import com.kalshiweather.ingestion.domain.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketRepository extends JpaRepository<Market, String> {

    List<Market> findBySeriesTicker(String seriesTicker);
}
