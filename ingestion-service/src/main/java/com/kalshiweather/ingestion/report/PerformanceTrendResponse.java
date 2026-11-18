package com.kalshiweather.ingestion.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PerformanceTrendResponse(
        TrendGranularity granularity,
        List<PeriodBucket> periods
) {

    public record PeriodBucket(
            LocalDate periodStart,
            List<DomainBucket> domains
    ) {
    }

    /**
     * {@code modelBrier}/{@code marketBrier} are the mean squared error of each probability
     * against the real resolved outcome (lower is better; 0.25 is what always guessing 50%
     * scores) — only computed over the subset of {@code n} trades where ground truth is
     * actually available ({@code brierSampleSize}; weather uses actual_observations, other
     * domains use the market's own settlement result). {@code edgeScore} is
     * {@code marketBrier - modelBrier}: positive means the model is genuinely better
     * calibrated than the market on this slice, negative (the current reality, as of
     * 2026-11-18) means the market is still beating us. All three are null when
     * {@code brierSampleSize} is zero.
     */
    public record DomainBucket(
            String domain,
            int n,
            BigDecimal winRate,
            BigDecimal totalPnl,
            BigDecimal avgPnl,
            int brierSampleSize,
            BigDecimal modelBrier,
            BigDecimal marketBrier,
            BigDecimal edgeScore
    ) {
    }
}
