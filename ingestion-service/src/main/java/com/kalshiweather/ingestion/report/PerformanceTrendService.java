package com.kalshiweather.ingestion.report;

import com.kalshiweather.ingestion.domain.entity.ActualObservation;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.entity.Signal;
import com.kalshiweather.ingestion.domain.enums.MarketResult;
import com.kalshiweather.ingestion.domain.enums.TradeStatus;
import com.kalshiweather.ingestion.repository.ActualObservationRepository;
import com.kalshiweather.ingestion.repository.EnsembleForecastRepository;
import com.kalshiweather.ingestion.repository.MarketRepository;
import com.kalshiweather.ingestion.repository.PaperTradeRepository;
import com.kalshiweather.ingestion.repository.SignalRepository;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.DomainBucket;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.PeriodBucket;
import com.kalshiweather.ingestion.service.ActualObservationService;
import com.kalshiweather.ingestion.signal.StrikeOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buckets all closed trades by calendar period (week or month) and domain (weather vs. each
 * sport), reporting both the $ side (win rate, total/avg P&L — what every manual check-in
 * this project has been hand-computing via ad hoc SQL) and the calibration side (model vs.
 * market Brier score) together, trended over time. The calibration half only became possible
 * once {@link ActualObservationService}'s ground-truth backfill had enough history to use —
 * before that, "edge" could only be inferred indirectly from win-rate-vs-market-price.
 *
 * Ground truth for "did this market resolve YES" comes from two different places depending on
 * domain: weather uses the real observed temperature ({@link ActualObservation}, compared
 * against the market's strike via {@link StrikeOutcome}) since a specific market's own
 * {@code result} column can be stale even when the day's actual temperature is already known
 * from a different settled bin for the same city/day; non-weather domains (MLB, future
 * sports) have no such table, so they use the market's own settlement {@code result} directly.
 */
@Service
public class PerformanceTrendService {

    private static final int SCALE = 4;

    private final PaperTradeRepository paperTradeRepository;
    private final SignalRepository signalRepository;
    private final MarketRepository marketRepository;
    private final EnsembleForecastRepository ensembleForecastRepository;
    private final ActualObservationRepository actualObservationRepository;

    public PerformanceTrendService(
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

    public PerformanceTrendResponse trend(TrendGranularity granularity) {
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

        Map<LocalDate, Map<String, List<AttributedTrade>>> byPeriodAndDomain = new TreeMap<>();
        for (PaperTrade trade : closed) {
            Signal signal = signalsById.get(trade.getSignalId());
            if (signal == null || trade.getClosedAt() == null) {
                continue; // defensive — shouldn't happen for a real closed trade
            }
            Market market = marketsById.get(trade.getMarketId());
            String domain = classifyDomain(signal);
            Boolean resolvedYes = resolveGroundTruth(signal, market, forecastsById, actualHighByStationDay);
            LocalDate periodStart = periodStart(trade.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate(), granularity);

            byPeriodAndDomain
                    .computeIfAbsent(periodStart, p -> new TreeMap<>())
                    .computeIfAbsent(domain, d -> new ArrayList<>())
                    .add(new AttributedTrade(trade, signal, resolvedYes));
        }

        List<PeriodBucket> periods = byPeriodAndDomain.entrySet().stream()
                .map(entry -> new PeriodBucket(
                        entry.getKey(),
                        entry.getValue().entrySet().stream()
                                .map(domainEntry -> buildDomainBucket(domainEntry.getKey(), domainEntry.getValue()))
                                .toList()))
                .toList();

        return new PerformanceTrendResponse(granularity, periods);
    }

    /** Mirrors DashboardController's simpler core (weather iff a forecast-backed signal;
     * otherwise the sourceType prefix) — doesn't need DashboardController's "not classifiable
     * yet" fallback, since every signal passed in here, by construction, already has one or
     * the other populated. */
    private String classifyDomain(Signal signal) {
        if (signal.getForecastId() != null) {
            return "WEATHER";
        }
        String sourceType = signal.getSourceType();
        return sourceType == null ? "UNKNOWN" : sourceType.split("_", 2)[0];
    }

    private Boolean resolveGroundTruth(
            Signal signal, Market market, Map<UUID, EnsembleForecast> forecastsById, Map<String, BigDecimal> actualHighByStationDay
    ) {
        if (signal.getForecastId() != null) {
            EnsembleForecast forecast = forecastsById.get(signal.getForecastId());
            if (forecast == null || market == null || market.getStrikeType() == null) {
                return null;
            }
            BigDecimal actualHigh = actualHighByStationDay.get(stationDayKey(forecast.getSubjectKey(), forecast.getValidFor()));
            return actualHigh == null ? null : StrikeOutcome.resolvesYes(market, actualHigh);
        }
        if (market == null || market.getResult() == null) {
            return null;
        }
        return market.getResult() == MarketResult.YES;
    }

    private String stationDayKey(String subjectKey, LocalDate date) {
        return subjectKey + "|" + date;
    }

    private LocalDate periodStart(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private DomainBucket buildDomainBucket(String domain, List<AttributedTrade> trades) {
        int n = trades.size();
        long wins = trades.stream().filter(t -> t.trade.getPnl().signum() > 0).count();
        BigDecimal winRate = BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);
        BigDecimal totalPnl = trades.stream().map(t -> t.trade.getPnl()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal avgPnl = totalPnl.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);

        List<AttributedTrade> withGroundTruth = trades.stream().filter(t -> t.resolvedYes != null).toList();
        BigDecimal modelBrier = null;
        BigDecimal marketBrier = null;
        BigDecimal edgeScore = null;
        if (!withGroundTruth.isEmpty()) {
            modelBrier = brierScore(withGroundTruth, t -> t.signal.getModelProbability());
            marketBrier = brierScore(withGroundTruth, t -> t.signal.getMarketImpliedProbability());
            edgeScore = marketBrier.subtract(modelBrier);
        }

        return new DomainBucket(domain, n, winRate, totalPnl, avgPnl, withGroundTruth.size(), modelBrier, marketBrier, edgeScore);
    }

    private BigDecimal brierScore(List<AttributedTrade> trades, Function<AttributedTrade, BigDecimal> probabilityOf) {
        BigDecimal sumSquaredError = trades.stream()
                .map(t -> {
                    BigDecimal outcome = Boolean.TRUE.equals(t.resolvedYes) ? BigDecimal.ONE : BigDecimal.ZERO;
                    BigDecimal error = probabilityOf.apply(t).subtract(outcome);
                    return error.multiply(error);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumSquaredError.divide(BigDecimal.valueOf(trades.size()), SCALE, RoundingMode.HALF_UP);
    }

    private record AttributedTrade(PaperTrade trade, Signal signal, Boolean resolvedYes) {
    }
}
