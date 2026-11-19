package com.kalshiweather.ingestion.calibration;

import com.kalshiweather.ingestion.domain.entity.BucketCalibration;
import com.kalshiweather.ingestion.domain.entity.PaperTrade;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;
import com.kalshiweather.ingestion.repository.BucketCalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recomputes, in full, whether each (domain, strikeType, direction) bucket has actually beaten
 * the market on calibration — the live gate in {@code SignalEligibility.hasProvenCalibration}
 * only ever reads what this wrote; it never computes ground truth itself. Runs on the same
 * once-daily cadence as {@code ActualObservationService} (settlement history doesn't change
 * intraday), plus a token-gated manual trigger for the initial backfill right after deploy.
 *
 * Recomputes the whole table every run (delete-all-then-reinsert inside one transaction)
 * rather than an incremental upsert — at this data volume (a few hundred closed trades,
 * a handful of buckets) a full recompute is simpler and removes any risk of a bucket's
 * sample size silently drifting out of sync with reality.
 */
@Service
public class BucketCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(BucketCalibrationService.class);
    private static final int BRIER_SCALE = 4;

    private final GroundTruthResolver groundTruthResolver;
    private final BucketCalibrationRepository bucketCalibrationRepository;

    public BucketCalibrationService(GroundTruthResolver groundTruthResolver, BucketCalibrationRepository bucketCalibrationRepository) {
        this.groundTruthResolver = groundTruthResolver;
        this.bucketCalibrationRepository = bucketCalibrationRepository;
    }

    @Scheduled(
            initialDelayString = "${calibration.schedule.initial-delay-ms:30000}",
            fixedDelayString = "${calibration.schedule.fixed-delay-ms:86400000}"
    )
    @Transactional
    public void recompute() {
        List<ResolvedTrade> withGroundTruth = groundTruthResolver.resolveAll().stream()
                .filter(t -> t.resolvedYes() != null)
                .toList();

        Map<BucketKey, List<ResolvedTrade>> byBucket = withGroundTruth.stream()
                .collect(Collectors.groupingBy(t -> new BucketKey(t.domain(), t.strikeType(), t.signal().getDirection())));

        List<BucketCalibration> fresh = new ArrayList<>();
        for (Map.Entry<BucketKey, List<ResolvedTrade>> entry : byBucket.entrySet()) {
            fresh.add(buildCalibration(entry.getKey(), entry.getValue()));
        }

        bucketCalibrationRepository.deleteAll();
        bucketCalibrationRepository.saveAll(fresh);
        log.info("Recomputed calibration for {} bucket(s) from {} ground-truthed trade(s)", fresh.size(), withGroundTruth.size());
    }

    private BucketCalibration buildCalibration(BucketKey key, List<ResolvedTrade> trades) {
        BucketCalibration calibration = new BucketCalibration();
        calibration.setDomain(key.domain());
        calibration.setStrikeType(key.strikeType());
        calibration.setDirection(key.direction());
        calibration.setSampleSize(trades.size());
        calibration.setModelBrier(brierScore(trades, t -> t.signal().getModelProbability()));
        calibration.setMarketBrier(brierScore(trades, t -> t.signal().getMarketImpliedProbability()));
        calibration.setComputedAt(Instant.now());
        return calibration;
    }

    private BigDecimal brierScore(List<ResolvedTrade> trades, Function<ResolvedTrade, BigDecimal> probabilityOf) {
        BigDecimal sumSquaredError = trades.stream()
                .map(t -> {
                    BigDecimal outcome = Boolean.TRUE.equals(t.resolvedYes()) ? BigDecimal.ONE : BigDecimal.ZERO;
                    BigDecimal error = probabilityOf.apply(t).subtract(outcome);
                    return error.multiply(error);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumSquaredError.divide(BigDecimal.valueOf(trades.size()), BRIER_SCALE, RoundingMode.HALF_UP);
    }

    private record BucketKey(String domain, StrikeType strikeType, SignalDirection direction) {
    }
}
