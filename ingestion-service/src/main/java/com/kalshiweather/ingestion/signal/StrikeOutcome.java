package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.Market;

import java.math.BigDecimal;

/**
 * Whether a single numeric value (an ensemble member, or a real observed high) falls on the
 * YES side of a market's strike — extracted out of {@link EmpiricalCdfSignalProvider} so
 * ground-truth outcome resolution (e.g. scoring a settled market's real temperature against
 * its strike for calibration reporting) can reuse the exact same logic instead of risking a
 * second, subtly different copy.
 */
public final class StrikeOutcome {

    private StrikeOutcome() {
    }

    public static boolean resolvesYes(Market market, BigDecimal value) {
        return switch (market.getStrikeType()) {
            case GREATER -> value.compareTo(market.getFloorStrike()) > 0;
            case LESS -> value.compareTo(market.getCapStrike()) < 0;
            case BETWEEN -> value.compareTo(market.getFloorStrike()) > 0
                    && value.compareTo(market.getCapStrike()) <= 0;
        };
    }
}
