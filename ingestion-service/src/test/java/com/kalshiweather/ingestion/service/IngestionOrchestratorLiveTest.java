package com.kalshiweather.ingestion.service;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real ingestion cycle end to end against the live Kalshi/Open-Meteo/NWS APIs and
 * the local Postgres — calls the orchestrator directly rather than waiting on
 * {@code @Scheduled}. Persists real rows into the local dev DB, which is the point: the
 * design doc's build order calls for verifying against real data by querying the DB
 * manually. Not wired into CI.
 */
@SpringBootTest
class IngestionOrchestratorLiveTest {

    @Autowired
    private IngestionOrchestrator orchestrator;

    @Autowired
    private MarketRepository marketRepository;

    @Autowired
    private EnsembleForecastRepository ensembleForecastRepository;

    @Test
    void runIngestionCycle_persistsMarketsAndForecastsForNyc() {
        orchestrator.runIngestionCycle();

        List<Market> markets = marketRepository.findBySeriesTicker("KXHIGHNY");
        assertThat(markets).isNotEmpty();

        LocalDate firstMarketDate = markets.get(0).getOccurrenceDate();
        List<EnsembleForecast> forecasts = ensembleForecastRepository.findBySubjectKeyAndValidFor("NYC", firstMarketDate);
        assertThat(forecasts)
                .as("expected both an OPEN_METEO_ENSEMBLE and an NWS forecast for %s", firstMarketDate)
                .extracting(EnsembleForecast::getSource)
                .contains("OPEN_METEO_ENSEMBLE", "NWS");
    }
}
