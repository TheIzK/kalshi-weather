package com.kalshiweather.ingestion.calibration;

import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.service.ActualObservationService;
import com.kalshiweather.ingestion.signal.SignalDomain;
import com.kalshiweather.ingestion.signal.StrikeOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attributes ground truth (did the market actually resolve YES) to every closed trade —
 * shared by {@code PerformanceTrendService} (periodic $ + calibration reporting) and
 * {@code BucketCalibrationService} (the live trade-gating calibration check), so both
 * consumers can't drift into two subtly different copies of this join/resolution logic.
 *
 * Ground truth comes from two different places depending on domain: weather uses the real
 * observed temperature ({@link ActualObservation}, compared against the market's strike via
 * {@link StrikeOutcome}), since a specific market's own {@code result} column can be stale
 * even when the day's actual temperature is already known from a different settled bin for
 * the same city/day. Non-weather domains use the trade's own {@code exitPrice} (combined with
 * {@code side}) rather than the market's {@code result} column directly — confirmed via real
 * MLB data (2026-11-19) that {@code exitPrice} and {@code market.result} don't always agree in
 * practice; {@code exitPrice} is the one set definitively by SettlementReconciliationService at
 * close time and doesn't depend on the markets table staying fresh afterward, so it's the more
 * trustworthy source for sports the way actual_observations is for weather.
 */
@Component
public class GroundTruthResolver {

    private final PaperTradeRepository paperTradeRepository;
    private final SignalRepository signalRepository;
    private final MarketRepository marketRepository;
    private final EnsembleForecastRepository ensembleForecastRepository;
    private final ActualObservationRepository actualObservationRepository;

    public GroundTruthResolver(
            PaperTradeRepository paperTradeRepository,
            SignalRepository signalRepository,
            MarketRepository marketRepository,
            EnsembleForecastRepository ensembleForecastRepository,
            ActualObservationRepository actualObservationRepository
    ) {
        this.paperTradeRepository = paperTradeRepository;
        this.signalRepository = signalRepository;
        this.marketRepository = marketRepository;
        this.ensembleForecastRepository = ensembleForecastRepository;
        this.actualObservationRepository = actualObservationRepository;
    }

    public List<ResolvedTrade> resolveAll() {
        List<PaperTrade> closed = paperTradeRepository.findByStatus(TradeStatus.CLOSED);

        Map<UUID, Signal> signalsById = signalRepository
                .findAllById(closed.stream().map(PaperTrade::getSignalId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Signal::getId, Function.identity()));

        Map<String, Market> marketsById = marketRepository
                .findAllById(closed.stream().map(PaperTrade::getMarketId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Market::getId, Function.identity()));

        Map<UUID, EnsembleForecast> forecastsById = ensembleForecastRepository
                .findAllById(signalsById.values().stream().map(Signal::getForecastId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(EnsembleForecast::getId, Function.identity()));

        Map<String, BigDecimal> actualHighByStationDay = new HashMap<>();
        for (String subjectKey : forecastsById.values().stream().map(EnsembleForecast::getSubjectKey).distinct().toList()) {
            for (ActualObservation observation : actualObservationRepository
                    .findBySubjectKeyAndSource(subjectKey, ActualObservationService.SOURCE_KALSHI_SETTLEMENT)) {
                actualHighByStationDay.put(stationDayKey(subjectKey, observation.getOccurrenceDate()), observation.getActualHighF());
            }
        }

        return closed.stream()
                .map(trade -> {
                    Signal signal = signalsById.get(trade.getSignalId());
                    if (signal == null) {
                        return null; // defensive — shouldn't happen for a real closed trade
                    }
                    Market market = marketsById.get(trade.getMarketId());
                    String domain = SignalDomain.classify(signal);
                    Boolean resolvedYes = resolveGroundTruth(trade, signal, market, forecastsById, actualHighByStationDay);
                    return new ResolvedTrade(trade, signal, market, domain, market != null ? market.getStrikeType() : null, resolvedYes);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Boolean resolveGroundTruth(
            PaperTrade trade, Signal signal, Market market,
            Map<UUID, EnsembleForecast> forecastsById, Map<String, BigDecimal> actualHighByStationDay
    ) {
        if (signal.getForecastId() != null) {
            EnsembleForecast forecast = forecastsById.get(signal.getForecastId());
            if (forecast == null || market == null || market.getStrikeType() == null) {
                return null;
            }
            BigDecimal actualHigh = actualHighByStationDay.get(stationDayKey(forecast.getSubjectKey(), forecast.getValidFor()));
            return actualHigh == null ? null : StrikeOutcome.resolvesYes(market, actualHigh);
        }
        if (trade.getExitPrice() == null) {
            return null;
        }
        boolean won = trade.getExitPrice().signum() > 0; // exitPrice is 1.0 (won) or 0.0 (lost), never fractional
        return trade.getSide() == SignalDirection.BUY_YES ? won : !won;
    }

    private String stationDayKey(String subjectKey, LocalDate date) {
        return subjectKey + "|" + date;
    }
}
