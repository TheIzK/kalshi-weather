package com.kalshiweather.ingestion.mlb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Maps GET /api/v1/people/{id}/stats?stats=season&group=pitching&season=YYYY */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlbPitcherStatsResponse(
        List<StatGroup> stats
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatGroup(List<Split> splits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Split(PitchingStat stat) {
    }

    /**
     * innings pitched is a wire string like "142.1" — the fractional part is OUTS, not
     * tenths. See FipCalculator.parseInningsPitched for the conversion. era/whip are also
     * wire strings (verified against live data 2026-08-16, e.g. "4.15") — typed directly as
     * BigDecimal here since Jackson parses a JSON string source into BigDecimal natively,
     * same pattern KalshiMarketDto already uses for its price fields.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PitchingStat(
            String inningsPitched,
            BigDecimal era,
            BigDecimal whip,
            int strikeOuts,
            int baseOnBalls,
            int hitByPitch,
            int homeRuns,
            int gamesStarted
    ) {
    }
}
