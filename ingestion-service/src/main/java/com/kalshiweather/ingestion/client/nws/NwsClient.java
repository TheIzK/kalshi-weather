package com.kalshiweather.ingestion.client.nws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * NWS's API is public but blocks requests without a descriptive User-Agent
 * (see https://www.weather.gov/documentation/services-web-api). Temperatures come back
 * already in Fahrenheit ({@code units: "us"}), so no unit conversion is needed here,
 * unlike Open-Meteo.
 */
@Component
public class NwsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NwsClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.weather.gov")
                .defaultHeader(HttpHeaders.USER_AGENT, "kalshi-weather (isaacmelton.dev, isaaclmelton@gmail.com)")
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves lat/lon to an NWS grid office + x/y. Call this once per station (e.g. when
     * seeding {@code subject_stations}) — the design doc is explicit this should be cached,
     * not re-fetched every ingestion cycle.
     */
    public GridPoint lookupGridPoint(double latitude, double longitude) {
        NwsPointsResponse response = restClient.get()
                .uri("/points/{lat},{lon}", latitude, longitude)
                .retrieve()
                .body(NwsPointsResponse.class);

        NwsPointsResponse.Properties props = Objects.requireNonNull(response, "empty /points response").properties();
        return new GridPoint(props.gridId(), props.gridX(), props.gridY());
    }

    /** Fetches the forecast for one subject/day, picking the matching daytime period. */
    public EnsembleForecast fetchDailyHighTemp(
            String subjectKey, String gridId, int gridX, int gridY, LocalDate targetDate
    ) {
        String rawJson = restClient.get()
                .uri("/gridpoints/{gridId}/{x},{y}/forecast", gridId, gridX, gridY)
                .retrieve()
                .body(String.class);

        NwsForecastResponse parsed = parse(rawJson);
        NwsForecastResponse.Period period = findDaytimePeriod(parsed.properties().periods(), targetDate)
                .orElseThrow(() -> new IllegalStateException(
                        "No NWS daytime period for subject " + subjectKey + " on " + targetDate));

        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setSubjectKey(subjectKey);
        forecast.setSource("NWS");
        forecast.setModelFamily(null);
        forecast.setObservedAt(Instant.now());
        forecast.setValidFor(targetDate);
        forecast.setPredictedValueF(period.temperature());
        forecast.setMemberValuesF(null);
        forecast.setPrecipProbabilityPercent(
                period.probabilityOfPrecipitation() != null ? period.probabilityOfPrecipitation().value() : null
        );
        forecast.setRawPayload(rawJson);
        return forecast;
    }

    /**
     * NWS periods split day/night; take the daytime period whose *local* start date matches,
     * not just "the next period in the array" (a real gotcha the design doc calls out).
     */
    private static java.util.Optional<NwsForecastResponse.Period> findDaytimePeriod(
            List<NwsForecastResponse.Period> periods, LocalDate targetDate
    ) {
        return periods.stream()
                .filter(NwsForecastResponse.Period::isDaytime)
                .filter(p -> p.startTime().toLocalDate().equals(targetDate))
                .findFirst();
    }

    private NwsForecastResponse parse(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, NwsForecastResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse NWS forecast response", e);
        }
    }

    public record GridPoint(String gridId, int gridX, int gridY) {
    }
}
