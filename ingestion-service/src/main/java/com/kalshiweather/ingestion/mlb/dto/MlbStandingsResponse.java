package com.kalshiweather.ingestion.mlb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Maps GET /api/v1/standings?leagueId=103,104&season=YYYY */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlbStandingsResponse(
        List<StandingsRecord> records
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StandingsRecord(List<TeamRecord> teamRecords) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRecord(
            TeamRef team,
            int wins,
            int losses,
            RecordSplits records   // verified against live data 2026-08-16: this is a nested
                                    // object ({splitRecords, divisionRecords, ...}), not a flat
                                    // list as originally assumed
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(int id, String name, String abbreviation) {
    }

    /** Only splitRecords is used (filter by type == "home" / "away"); the rest
     * (divisionRecords, overallRecords, leagueRecords, expectedRecords) are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecordSplits(List<SplitRecord> splitRecords) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SplitRecord(String type, int wins, int losses) {
    }
}
