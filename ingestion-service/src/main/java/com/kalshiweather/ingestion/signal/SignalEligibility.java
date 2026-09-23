package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.SignalConfig;
import com.kalshiweather.ingestion.domain.enums.SignalDirection;
import com.kalshiweather.ingestion.domain.enums.StrikeType;

import java.math.BigDecimal;

/**
 * The gating checks a candidate trade must pass, extracted out of {@link SignalGenerationService}
 * so the live trading path and backtest replay can't drift apart by each implementing their own
 * copy. Stateless — every method is a pure function of its arguments.
 */
public final class SignalEligibility {

    private SignalEligibility() {
    }

    /** Guardrail: BETWEEN markets (narrow, fixed-width temperature bins, e.g. "82-83F") are
     * a persistent structural loser, unlike GREATER/LESS (open-ended tail bins) — full trade
     * history through 2026-09-08: BETWEEN -$22.30 over 466 trades (-4.8c/trade avg), GREATER
     * +$1.92 (65 trades), LESS +$1.65 post-guardrail. Consistent across all 7 cities, not a
     * location- or time-window-specific fluke. Likely mechanism: a narrow bin's empirical-CDF
     * probability is far more sensitive to ensemble sampling noise than a wide tail's
     * cumulative probability, given ~119-122 members. Doesn't touch GREATER/LESS or non-weather
     * sources (MLB markets have no strikeType). Reversible via config, not a model change. */
    public static boolean isExcludedByStrikeType(SignalConfig config, StrikeType strikeType) {
        return Boolean.TRUE.equals(config.getExcludeBetweenStrikeType()) && strikeType == StrikeType.BETWEEN;
    }

    /** Guardrail: LESS-strike markets where the model favors BUY_YES ("it'll be unusually
     * cold") have been a consistent, near-total loser in the post-BETWEEN-exclusion era
     * (2026-09-12 onward): -$2.35 over 25 trades (8.0% win rate), spanning all 7 cities across
     * 9 distinct days, while every other strike-type/direction combination in the same window
     * is net positive. Not a code or normal-fit-fallback artifact — confirmed the model
     * probability matches the raw empirical member count exactly in every case, including one
     * where all 119/119 ensemble members predicted cold and the market still settled warm.
     * Losing at every confidence level, up to near-unanimous ensemble agreement, looks like a
     * systematic cold-side bias rather than noise, though 9 days could still reflect one shared
     * weather regime rather than a durable pattern — reversible via config, not a model change,
     * while more data accumulates. Doesn't affect BUY_NO on LESS, GREATER either direction, or
     * non-weather sources (no strikeType). */
    public static boolean isExcludedByLessBuyYes(SignalConfig config, StrikeType strikeType, SignalDirection direction) {
        return Boolean.TRUE.equals(config.getExcludeLessBuyYes())
                && strikeType == StrikeType.LESS
                && direction == SignalDirection.BUY_YES;
    }

    /** Guardrail: don't fade the model's own best guess. Weather trades where the model's
     * stated probability for the side actually taken was under 50% ("long-shot" value bets,
     * e.g. buying YES at 15% because the market was pricing it even cheaper) went 0-for-45
     * across 28 independent city-days (2026-08-15 through 2026-08-26) — see calibration
     * investigation. Twelve calendar days isn't enough regime diversity to trust a
     * recalibrated probability curve yet, so this is a blunt, reversible floor rather than
     * a model change: only take signals the model itself thinks are more likely than not. */
    public static boolean meetsConfidenceFloor(SignalConfig config, SignalDirection direction, BigDecimal modelProbability) {
        if (config.getMinModelConfidencePercent() == null) {
            return true;
        }
        BigDecimal sideConfidence = direction == SignalDirection.BUY_YES
                ? modelProbability
                : BigDecimal.ONE.subtract(modelProbability);
        return sideConfidence.multiply(BigDecimal.valueOf(100)).compareTo(config.getMinModelConfidencePercent()) >= 0;
    }

    public static boolean clearsThreshold(
            SignalConfig config, BigDecimal edgePercent, BigDecimal netEdgePercent,
            BigDecimal modelProbability, int ensembleMemberCount
    ) {
        return switch (config.getThresholdMode()) {
            case FLAT_PERCENT -> config.getFlatThresholdPercent() != null
                    && edgePercent.compareTo(config.getFlatThresholdPercent()) >= 0;
            case FEE_ADJUSTED -> config.getMinNetEdgeAfterFees() != null
                    && netEdgePercent.compareTo(config.getMinNetEdgeAfterFees()) >= 0;
            case CONFIDENCE_ADJUSTED -> config.getMinZScore() != null
                    && zScore(edgePercent, modelProbability, ensembleMemberCount).compareTo(config.getMinZScore()) >= 0;
        };
    }

    /**
     * Not specified precisely in the design doc beyond the mode's name — interpreted here as
     * the edge (as a probability) divided by the binomial standard error of the empirical
     * ensemble probability (sqrt(p*(1-p)/n)), i.e. "how many standard errors is the edge
     * away from noise, given how many ensemble members we're estimating from." Zero for
     * non-ensemble sources (n=0), which correctly never clears a positive threshold.
     */
    private static BigDecimal zScore(BigDecimal edgePercent, BigDecimal modelProbability, int ensembleMemberCount) {
        if (ensembleMemberCount == 0) {
            return BigDecimal.ZERO;
        }
        double p = modelProbability.doubleValue();
        double standardError = Math.sqrt(p * (1 - p) / ensembleMemberCount);
        if (standardError == 0) {
            return BigDecimal.valueOf(Double.MAX_VALUE); // a degenerate, unanimous ensemble is maximally confident
        }
        double edgeFraction = edgePercent.doubleValue() / 100.0;
        return BigDecimal.valueOf(edgeFraction / standardError);
    }
}
