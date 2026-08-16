package com.kalshiweather.ingestion.mlb.client;

import com.kalshiweather.ingestion.mlb.dto.MlbPitcherStatsResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbScheduleResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbStandingsResponse;

import java.time.LocalDate;

public interface MlbStatsApiClient {

    /** GET /schedule?sportId=1&date={date}&hydrate=team,linescore,probablePitcher */
    MlbScheduleResponse getScheduleForDate(LocalDate date);

    /** GET /people/{playerId}/stats?stats=season&group=pitching&season={season} */
    MlbPitcherStatsResponse getPitcherSeasonStats(int playerId, int season);

    /** GET /standings?leagueId=103,104&season={season}  (103 = AL, 104 = NL) */
    MlbStandingsResponse getStandings(int season);
}
