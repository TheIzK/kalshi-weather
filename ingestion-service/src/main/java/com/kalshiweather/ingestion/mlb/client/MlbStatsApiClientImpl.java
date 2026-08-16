package com.kalshiweather.ingestion.mlb.client;

import com.kalshiweather.ingestion.mlb.dto.MlbPitcherStatsResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbScheduleResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbStandingsResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** No API key required — statsapi.mlb.com is unauthenticated and public. */
@Component
public class MlbStatsApiClientImpl implements MlbStatsApiClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestClient restClient;

    public MlbStatsApiClientImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://statsapi.mlb.com/api/v1")
                .build();
    }

    @Override
    public MlbScheduleResponse getScheduleForDate(LocalDate date) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/schedule")
                        .queryParam("sportId", 1)
                        .queryParam("date", date.format(DATE_FMT))
                        .queryParam("hydrate", "team,linescore,probablePitcher")
                        .build())
                .retrieve()
                .body(MlbScheduleResponse.class);
    }

    @Override
    public MlbPitcherStatsResponse getPitcherSeasonStats(int playerId, int season) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/people/{playerId}/stats")
                        .queryParam("stats", "season")
                        .queryParam("group", "pitching")
                        .queryParam("season", season)
                        .build(playerId))
                .retrieve()
                .body(MlbPitcherStatsResponse.class);
    }

    @Override
    public MlbStandingsResponse getStandings(int season) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/standings")
                        .queryParam("leagueId", "103,104")
                        .queryParam("season", season)
                        .build())
                .retrieve()
                .body(MlbStandingsResponse.class);
    }
}
