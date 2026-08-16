package com.kalshiweather.ingestion.mlb.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FIP = ((13*HR) + (3*(BB+HBP)) - (2*K)) / IP + constant
 *
 * The constant recenters FIP onto the league's actual ERA scale for the season; 3.10 is a
 * reasonable fixed placeholder for v1. To do this properly, compute it per season as:
 * leagueERA - ((13*leagueHR + 3*(leagueBB+leagueHBP) - 2*leagueK) / leagueIP) from
 * aggregate league totals, refreshed once per season.
 */
@Component
public class FipCalculator {

    private static final BigDecimal FIP_CONSTANT = new BigDecimal("3.10");

    public BigDecimal calculate(int homeRunsAllowed, int walks, int hitBatsmen, int strikeouts, BigDecimal inningsPitched) {
        if (inningsPitched.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("innings pitched must be > 0");
        }
        BigDecimal numerator = BigDecimal.valueOf(13L * homeRunsAllowed)
                .add(BigDecimal.valueOf(3L * (walks + hitBatsmen)))
                .subtract(BigDecimal.valueOf(2L * strikeouts));

        return numerator.divide(inningsPitched, 4, RoundingMode.HALF_UP).add(FIP_CONSTANT);
    }

    /**
     * statsapi returns innings pitched as e.g. "142.1" where the fractional part is OUTS,
     * not tenths -- .1 = one out (1/3 inning), .2 = two outs (2/3 inning). This converts to
     * a true decimal value for arithmetic.
     */
    public BigDecimal parseInningsPitched(String raw) {
        String[] parts = raw.split("\\.");
        int wholeInnings = Integer.parseInt(parts[0]);
        int outs = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        BigDecimal fractional = switch (outs) {
            case 1 -> new BigDecimal("0.3333");
            case 2 -> new BigDecimal("0.6667");
            default -> BigDecimal.ZERO;
        };
        return BigDecimal.valueOf(wholeInnings).add(fractional);
    }
}
