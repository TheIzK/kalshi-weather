package com.kalshiweather.ingestion.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestResponse(
        BacktestRequest candidateConfig,
        WindowStats train,
        WindowStats test
) {

    /**
     * {@code historicalSignalCount} is the hard ceiling: this replay only ever filters the
     * already-persisted {@code Signal} population for the window, so {@code retainedCount} can
     * never exceed it. A looser candidate config than any that ever actually ran live cannot
     * recover trades that were never signaled — their price snapshot no longer exists. This
     * answers "of the trades we actually took, which subset survives under this config, and
     * how did it perform" — not "what new trades would this config have caught."
     */
    public record WindowStats(
            Instant windowStart,
            Instant windowEnd,
            int historicalSignalCount,
            int retainedCount,
            int excludedByStrikeType,
            int excludedByConfidenceFloor,
            int excludedByThreshold,
            int excludedMissingTrade,
            int settledCount,
            int openCount,
            BigDecimal winRate,
            BigDecimal totalPnl,
            BigDecimal avgPnl
    ) {
    }
}
