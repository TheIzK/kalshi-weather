package com.kalshiweather.ingestion.mlb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Maps GET /api/v1/schedule?sportId=1&date=YYYY-MM-DD&hydrate=team,linescore,probablePitcher
 * Only the fields we actually use are mapped; @JsonIgnoreProperties(ignoreUnknown = true)
 * lets the rest of the (large) statsapi payload pass through unmapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlbScheduleResponse(
        List<ScheduleDate> dates
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleDate(String date, List<ScheduleGame> games) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleGame(
            long gamePk,
            String gameDate,          // ISO-8601 UTC instant — do NOT derive the calendar date from
                                       // this (a late game's UTC date can roll to the next day); use
                                       // officialDate instead, matching this repo's date-normalization rule.
            String officialDate,      // the calendar day MLB assigns the game, e.g. "2026-08-16"
            GameStatus status,
            Teams teams,
            String gameType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GameStatus(String detailedState) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Teams(TeamSide home, TeamSide away) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamSide(TeamRef team, ProbablePitcherRef probablePitcher) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(int id, String name, String abbreviation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProbablePitcherRef(int id, String fullName) {
    }
}
