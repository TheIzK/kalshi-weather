package com.kalshiweather.ingestion.report;

import com.kalshiweather.ingestion.calibration.GroundTruthResolver;
import com.kalshiweather.ingestion.calibration.ResolvedTrade;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.DomainBucket;
import com.kalshiweather.ingestion.report.PerformanceTrendResponse.PeriodBucket;
import com.kalshiweather.ingestion.service.ActualObservationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Buckets all closed trades by calendar period (week or month) and domain (weather vs. each
 * sport), reporting both the $ side (win rate, total/avg P&L — what every manual check-in
 * this project has been hand-computing via ad hoc SQL) and the calibration side (model vs.
 * market Brier score) together, trended over time. The calibration half only became possible
 * once {@link ActualObservationService}'s ground-truth backfill had enough history to use —
 * before that, "edge" could only be inferred indirectly from win-rate-vs-market-price.
 *
 * Ground-truth resolution itself (which domain, did the market actually resolve YES) is
 * delegated to {@link GroundTruthResolver}, shared with {@code BucketCalibrationService} so
 * the two consumers of "what really happened" can't drift into different answers — this
 * service only does the period/domain bucketing and $ + Brier aggregation on top.
 */
@Service
public class PerformanceTrendService {

    private static final int SCALE = 4;

    private final GroundTruthResolver groundTruthResolver;

    public PerformanceTrendService(GroundTruthResolver groundTruthResolver) {
        this.groundTruthResolver = groundTruthResolver;
    }

    public PerformanceTrendResponse trend(TrendGranularity granularity) {
        Map<LocalDate, Map<String, List<ResolvedTrade>>> byPeriodAndDomain = new TreeMap<>();
        for (ResolvedTrade resolved : groundTruthResolver.resolveAll()) {
            if (resolved.trade().getClosedAt() == null) {
                continue; // defensive — shouldn't happen for a real closed trade
            }
            LocalDate periodStart = periodStart(resolved.trade().getClosedAt().atZone(ZoneOffset.UTC).toLocalDate(), granularity);
            byPeriodAndDomain
                    .computeIfAbsent(periodStart, p -> new TreeMap<>())
                    .computeIfAbsent(resolved.domain(), d -> new ArrayList<>())
                    .add(resolved);
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

    private LocalDate periodStart(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private DomainBucket buildDomainBucket(String domain, List<ResolvedTrade> trades) {
        int n = trades.size();
        long wins = trades.stream().filter(t -> t.trade().getPnl().signum() > 0).count();
        BigDecimal winRate = BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);
        BigDecimal totalPnl = trades.stream().map(t -> t.trade().getPnl()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal avgPnl = totalPnl.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);

        List<ResolvedTrade> withGroundTruth = trades.stream().filter(t -> t.resolvedYes() != null).toList();
        BigDecimal modelBrier = null;
        BigDecimal marketBrier = null;
        BigDecimal edgeScore = null;
        if (!withGroundTruth.isEmpty()) {
            modelBrier = brierScore(withGroundTruth, t -> t.signal().getModelProbability());
            marketBrier = brierScore(withGroundTruth, t -> t.signal().getMarketImpliedProbability());
            edgeScore = marketBrier.subtract(modelBrier);
        }

        return new DomainBucket(domain, n, winRate, totalPnl, avgPnl, withGroundTruth.size(), modelBrier, marketBrier, edgeScore);
    }

    private BigDecimal brierScore(List<ResolvedTrade> trades, Function<ResolvedTrade, BigDecimal> probabilityOf) {
        BigDecimal sumSquaredError = trades.stream()
                .map(t -> {
                    BigDecimal outcome = Boolean.TRUE.equals(t.resolvedYes()) ? BigDecimal.ONE : BigDecimal.ZERO;
                    BigDecimal error = probabilityOf.apply(t).subtract(outcome);
                    return error.multiply(error);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumSquaredError.divide(BigDecimal.valueOf(trades.size()), SCALE, RoundingMode.HALF_UP);
    }
}
