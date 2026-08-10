package com.kalshiweather.ingestion.signal;

import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import com.kalshiweather.ingestion.domain.entity.Market;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Design doc methodology:
 * 1. Primary: empirical CDF from ensemble members — P(above strike) = fraction of members
 *    past the strike.
 * 2. Fallback: parametric normal fit (mean/stddev of members), used only when the empirical
 *    estimate lands at exactly 0% or 100% — ~122 members is too few to trust in the extreme
 *    tails, which is exactly where real edges are most likely to hide.
 */
@Component
public class EmpiricalCdfSignalProvider implements SignalProvider {

    private static final int PROBABILITY_SCALE = 6;

    @Override
    public BigDecimal computeProbability(Market market, EnsembleForecast forecast) {
        BigDecimal[] members = forecast.getMemberValuesF();
        if (members == null || members.length == 0) {
            throw new IllegalArgumentException(
                    "EnsembleForecast " + forecast.getId() + " has no member values (source=" + forecast.getSource()
                            + ") — the empirical CDF method requires an ensemble forecast, not a single-point one");
        }

        BigDecimal empirical = empiricalProbability(market, members);
        boolean atExtreme = empirical.compareTo(BigDecimal.ZERO) == 0 || empirical.compareTo(BigDecimal.ONE) == 0;
        return atExtreme ? normalFitProbability(market, members) : empirical;
    }

    private BigDecimal empiricalProbability(Market market, BigDecimal[] members) {
        long favorable = 0;
        for (BigDecimal member : members) {
            if (resolvesYes(market, member)) {
                favorable++;
            }
        }
        return BigDecimal.valueOf(favorable)
                .divide(BigDecimal.valueOf(members.length), PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalFitProbability(Market market, BigDecimal[] members) {
        double[] values = new double[members.length];
        for (int i = 0; i < members.length; i++) {
            values[i] = members[i].doubleValue();
        }

        double mean = new Mean().evaluate(values);
        double stddev = new StandardDeviation().evaluate(values, mean);

        if (stddev == 0) {
            // Degenerate case: every member agrees exactly — the outcome isn't really in doubt.
            return resolvesYes(market, BigDecimal.valueOf(mean)) ? BigDecimal.ONE : BigDecimal.ZERO;
        }

        NormalDistribution normal = new NormalDistribution(mean, stddev);
        double probability = switch (market.getStrikeType()) {
            case GREATER -> 1 - normal.cumulativeProbability(market.getFloorStrike().doubleValue());
            case LESS -> normal.cumulativeProbability(market.getCapStrike().doubleValue());
            case BETWEEN -> normal.cumulativeProbability(market.getCapStrike().doubleValue())
                    - normal.cumulativeProbability(market.getFloorStrike().doubleValue());
        };

        // Clamp: the normal fit can theoretically slip fractionally outside [0, 1] from
        // floating-point error at the extremes.
        probability = Math.max(0, Math.min(1, probability));
        return BigDecimal.valueOf(probability).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean resolvesYes(Market market, BigDecimal value) {
        return switch (market.getStrikeType()) {
            case GREATER -> value.compareTo(market.getFloorStrike()) > 0;
            case LESS -> value.compareTo(market.getCapStrike()) < 0;
            case BETWEEN -> value.compareTo(market.getFloorStrike()) > 0
                    && value.compareTo(market.getCapStrike()) <= 0;
        };
    }
}
