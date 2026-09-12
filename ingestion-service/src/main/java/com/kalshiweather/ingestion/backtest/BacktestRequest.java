package com.kalshiweather.ingestion.backtest;

import com.kalshiweather.ingestion.domain.enums.ThresholdMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A candidate {@code SignalConfig} plus a walk-forward date split. {@code windowStart}
 * through {@code splitAt} is the train/calibration window; {@code splitAt} through
 * {@code windowEnd} is the untouched test window. The field names/semantics mirror
 * {@code SignalConfig} exactly so a candidate can be reasoned about the same way as a live
 * config.
 */
public record BacktestRequest(
        ThresholdMode thresholdMode,
        BigDecimal flatThresholdPercent,
        BigDecimal minNetEdgeAfterFees,
        BigDecimal minZScore,
        BigDecimal minModelConfidencePercent,
        Boolean excludeBetweenStrikeType,
        Instant windowStart,
        Instant splitAt,
        Instant windowEnd
) {
}
