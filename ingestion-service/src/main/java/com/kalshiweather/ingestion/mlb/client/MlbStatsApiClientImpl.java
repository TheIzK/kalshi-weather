package com.kalshiweather.ingestion.mlb.client;

import com.kalshiweather.ingestion.mlb.dto.MlbPitcherStatsResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbScheduleResponse;
import com.kalshiweather.ingestion.mlb.dto.MlbStandingsResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * No API key required — statsapi.mlb.com is unauthenticated and public.
 *
 * {@code getScheduleForDate}/{@code getStandings} build their URIs via
 * {@code UriComponentsBuilder...build(true)} (pre-encoded) rather than the usual
 * {@code uriBuilder -> ...} lambda — confirmed via live calls on 2026-10-06 that
 * statsapi.mlb.com's edge/WAF rejects requests with a literal, unencoded comma in a query
 * value (e.g. {@code hydrate=team,linescore,probablePitcher}) with a 406, but accepts the
 * identical value with the comma percent-encoded ({@code %2C}). Spring's default URI encoder
 * leaves commas unencoded in query values (RFC 3986 classifies comma as an allowed sub-delim),
 * which is spec-compliant but exactly what trips this. This had silently broken MLB signal
 * generation for ~10 days (2026-09-26 onward) before being caught — there's no live smoke test
 * for this client, unlike Kalshi/Open-Meteo/NWS.
 */
@Component
public class MlbStatsApiClientImpl implements MlbStatsApiClient {

    private static final String BASE_URL = "https://statsapi.mlb.com/api/v1";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestClient restClient;

    public MlbStatsApiClientImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    @Override
    public MlbScheduleResponse getScheduleForDate(LocalDate date) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/schedule")
                .queryParam("sportId", 1)
                .queryParam("date", date.format(DATE_FMT))
                .queryParam("hydrate", "team%2Clinescore%2CprobablePitcher")
                .build(true)
                .toUri();
        return restClient.get().uri(uri).retrieve().body(MlbScheduleResponse.class);
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
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/standings")
                .queryParam("leagueId", "103%2C104")
                .queryParam("season", season)
                .build(true)
                .toUri();
        return restClient.get().uri(uri).retrieve().body(MlbStandingsResponse.class);
    }
}
