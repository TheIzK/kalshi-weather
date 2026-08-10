package com.kalshiweather.ingestion.service;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.client.nws.NwsClient;
import com.kalshiweather.ingestion.client.openmeteo.OpenMeteoClient;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.SubjectStationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Runs one ingestion cycle per configured {@link SubjectStation}: pull open Kalshi markets,
 * then pull both weather sources for every date that actually has an open market (rather
 * than a fixed lookahead window). Raw storage only — no probability/signal logic here.
 *
 * Each source is isolated so one failing (a flaky API, a transient timeout) doesn't stop
 * the others from still being captured this cycle.
 */
@Component
public class IngestionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestionOrchestrator.class);

    private final SubjectStationRepository subjectStationRepository;
    private final MarketRepository marketRepository;
    private final EnsembleForecastRepository ensembleForecastRepository;
    private final KalshiClient kalshiClient;
    private final OpenMeteoClient openMeteoClient;
    private final NwsClient nwsClient;

    public IngestionOrchestrator(
            SubjectStationRepository subjectStationRepository,
            MarketRepository marketRepository,
            EnsembleForecastRepository ensembleForecastRepository,
            KalshiClient kalshiClient,
            OpenMeteoClient openMeteoClient,
            NwsClient nwsClient
    ) {
        this.subjectStationRepository = subjectStationRepository;
        this.marketRepository = marketRepository;
        this.ensembleForecastRepository = ensembleForecastRepository;
        this.kalshiClient = kalshiClient;
        this.openMeteoClient = openMeteoClient;
        this.nwsClient = nwsClient;
    }

    @Scheduled(
            initialDelayString = "${ingestion.schedule.initial-delay-ms:5000}",
            fixedDelayString = "${ingestion.schedule.fixed-delay-ms:900000}"
    )
    public void runIngestionCycle() {
        List<SubjectStation> stations = subjectStationRepository.findAll();
        if (stations.isEmpty()) {
            log.warn("No subject stations configured — nothing to ingest");
            return;
        }

        for (SubjectStation station : stations) {
            ingestStation(station);
        }
    }

    private void ingestStation(SubjectStation station) {
        Set<LocalDate> occurrenceDates = ingestMarkets(station);
        for (LocalDate date : occurrenceDates) {
            ingestOpenMeteo(station, date);
            ingestNws(station, date);
        }
    }

    private Set<LocalDate> ingestMarkets(SubjectStation station) {
        try {
            List<Market> markets = kalshiClient.fetchOpenMarkets(station.getSeriesTicker());
            marketRepository.saveAll(markets);
            log.info("Ingested {} open market(s) for {}", markets.size(), station.getSeriesTicker());
            return markets.stream()
                    .map(Market::getOccurrenceDate)
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (Exception e) {
            log.error("Kalshi ingestion failed for {}: {}", station.getSeriesTicker(), e.getMessage(), e);
            return Set.of();
        }
    }

    private void ingestOpenMeteo(SubjectStation station, LocalDate date) {
        try {
            EnsembleForecast forecast = openMeteoClient.fetchDailyHighTemp(
                    station.getSubjectKey(), station.getLatitude(), station.getLongitude(), date);
            ensembleForecastRepository.save(forecast);
            log.info("Ingested Open-Meteo forecast for {} on {}", station.getSubjectKey(), date);
        } catch (Exception e) {
            log.error("Open-Meteo ingestion failed for {} on {}: {}", station.getSubjectKey(), date, e.getMessage(), e);
        }
    }

    private void ingestNws(SubjectStation station, LocalDate date) {
        try {
            EnsembleForecast forecast = nwsClient.fetchDailyHighTemp(
                    station.getSubjectKey(), station.getNwsGridId(), station.getNwsGridX(), station.getNwsGridY(), date);
            ensembleForecastRepository.save(forecast);
            log.info("Ingested NWS forecast for {} on {}", station.getSubjectKey(), date);
        } catch (Exception e) {
            log.error("NWS ingestion failed for {} on {}: {}", station.getSubjectKey(), date, e.getMessage(), e);
        }
    }
}
