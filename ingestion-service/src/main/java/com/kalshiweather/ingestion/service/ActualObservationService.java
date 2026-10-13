package com.kalshiweather.ingestion.service;

import com.kalshiweather.ingestion.client.kalshi.KalshiClient;
import com.kalshiweather.ingestion.client.kalshi.KalshiMarketDto;
import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.SubjectStation;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.SubjectStationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Backfills ground-truth actual daily-high temperatures from Kalshi's own settled-market
 * history, rather than a separate weather-observations integration — every settled Kalshi
 * weather market already carries the literal observed high as {@code expiration_value}
 * (verified live 2026-10-13: multiple settled bins for the same city/day independently agree
 * on the same value). This is what finally makes real forecast-accuracy measurement possible
 * (e.g. a proper Brier score against our own ensemble forecasts), instead of inferring
 * calibration indirectly from win-rate-vs-market-price the way prior analysis had to.
 */
@Component
public class ActualObservationService {

    private static final Logger log = LoggerFactory.getLogger(ActualObservationService.class);

    public static final String SOURCE_KALSHI_SETTLEMENT = "KALSHI_SETTLEMENT";

    // Markets resolve against a US Eastern calendar day, same zone used everywhere else
    // occurrence dates are derived from a Kalshi Instant (KalshiClient.MARKET_ZONE, MLB's
    // MLB_ZONE) — redeclared locally per this repo's existing convention rather than shared.
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final SubjectStationRepository subjectStationRepository;
    private final ActualObservationRepository actualObservationRepository;
    private final KalshiClient kalshiClient;

    public ActualObservationService(
            SubjectStationRepository subjectStationRepository,
            ActualObservationRepository actualObservationRepository,
            KalshiClient kalshiClient
    ) {
        this.subjectStationRepository = subjectStationRepository;
        this.actualObservationRepository = actualObservationRepository;
        this.kalshiClient = kalshiClient;
    }

    /** Settlement history doesn't change intraday — once daily is plenty to pick up newly
     * settled days, same reasoning as MLB's standings/stats cadence. */
    @Scheduled(
            initialDelayString = "${actuals.schedule.initial-delay-ms:30000}",
            fixedDelayString = "${actuals.schedule.fixed-delay-ms:86400000}"
    )
    public void backfillAllSubjects() {
        for (SubjectStation station : subjectStationRepository.findAll()) {
            try {
                int added = backfillSubject(station);
                if (added > 0) {
                    log.info("Backfilled {} new actual observation(s) for {}", added, station.getSubjectKey());
                }
            } catch (Exception e) {
                log.error("Actual-observation backfill failed for {}: {}", station.getSubjectKey(), e.getMessage(), e);
            }
        }
    }

    /** Returns the number of newly persisted observations (for logging/testing); already-known
     * dates are skipped so repeated runs are cheap and idempotent. */
    public int backfillSubject(SubjectStation station) {
        List<KalshiMarketDto> settled = kalshiClient.fetchSettledMarkets(station.getSeriesTicker());
        if (settled.isEmpty()) {
            return 0;
        }

        Set<LocalDate> knownDates = actualObservationRepository
                .findBySubjectKeyAndSource(station.getSubjectKey(), SOURCE_KALSHI_SETTLEMENT)
                .stream()
                .map(ActualObservation::getOccurrenceDate)
                .collect(Collectors.toCollection(HashSet::new));

        Map<LocalDate, List<KalshiMarketDto>> byDate = settled.stream()
                .filter(dto -> dto.occurrenceDatetime() != null)
                .collect(Collectors.groupingBy(dto -> dto.occurrenceDatetime().atZone(MARKET_ZONE).toLocalDate()));

        int added = 0;
        for (Map.Entry<LocalDate, List<KalshiMarketDto>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (knownDates.contains(date)) {
                continue;
            }

            BigDecimal actualHighF = resolveAgreedValue(station.getSubjectKey(), date, entry.getValue());
            if (actualHighF == null) {
                continue; // no parseable value yet, or bins disagreed — skip, logged already
            }

            ActualObservation observation = new ActualObservation();
            observation.setSubjectKey(station.getSubjectKey());
            observation.setOccurrenceDate(date);
            observation.setActualHighF(actualHighF);
            observation.setSource(SOURCE_KALSHI_SETTLEMENT);
            observation.setFetchedAt(Instant.now());
            actualObservationRepository.save(observation);
            added++;
        }
        return added;
    }

    /** Every settled bin for the same subject/day should report the identical
     * expiration_value (they're all settling against the same real-world outcome) — this is
     * a direct cross-check of that assumption, not just a convenience dedup. Logs and skips
     * on disagreement rather than silently picking one, since that would indicate a real data
     * inconsistency worth looking into. */
    private BigDecimal resolveAgreedValue(String subjectKey, LocalDate date, List<KalshiMarketDto> bins) {
        Set<BigDecimal> distinctValues = bins.stream()
                .map(dto -> parseValue(dto.expirationValue()))
                .filter(v -> v != null)
                .collect(Collectors.toSet());

        if (distinctValues.isEmpty()) {
            return null;
        }
        if (distinctValues.size() > 1) {
            log.warn("Settled bins for {} on {} disagree on expiration_value: {} — skipping", subjectKey, date, distinctValues);
            return null;
        }
        return distinctValues.iterator().next();
    }

    private BigDecimal parseValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
