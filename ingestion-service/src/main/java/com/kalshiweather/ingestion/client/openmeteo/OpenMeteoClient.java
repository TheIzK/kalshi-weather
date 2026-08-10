package com.kalshiweather.ingestion.client.openmeteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshiweather.ingestion.domain.entity.EnsembleForecast;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Open-Meteo's ensemble API is public, free, no auth. Combining multiple model families
 * gives ~122 total ensemble members, but each member field gets a model-specific suffix
 * (e.g. "temperature_2m_max_member01_ncep_gefs_seamless") rather than a flat, predictable
 * name — so member extraction below is done by pattern rather than hardcoded field names.
 * https://ensemble-api.open-meteo.com/v1/ensemble
 */
@Component
public class OpenMeteoClient {

    private static final String MODELS = "gfs_seamless,ecmwf_ifs025,icon_seamless";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl("https://ensemble-api.open-meteo.com/v1")
                .build();
        this.objectMapper = objectMapper;
    }

    /** Fetches the daily-high-temperature ensemble for one subject/day, in Fahrenheit. */
    public EnsembleForecast fetchDailyHighTemp(
            String subjectKey, BigDecimal latitude, BigDecimal longitude, LocalDate targetDate
    ) {
        String rawJson = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/ensemble")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("daily", "temperature_2m_max")
                        .queryParam("temperature_unit", "celsius")
                        .queryParam("forecast_days", 7)
                        .queryParam("models", MODELS)
                        .build())
                .retrieve()
                .body(String.class);

        JsonNode root = parse(rawJson);
        JsonNode daily = root.path("daily");

        int dateIndex = indexOfDate(daily.path("time"), targetDate);
        if (dateIndex < 0) {
            throw new IllegalStateException(
                    "Open-Meteo response for subject " + subjectKey + " has no entry for " + targetDate);
        }

        List<BigDecimal> membersF = extractMembersInFahrenheit(daily, dateIndex);
        if (membersF.isEmpty()) {
            throw new IllegalStateException(
                    "Open-Meteo response for subject " + subjectKey + " on " + targetDate + " had no ensemble members");
        }

        EnsembleForecast forecast = new EnsembleForecast();
        forecast.setSubjectKey(subjectKey);
        forecast.setSource("OPEN_METEO_ENSEMBLE");
        forecast.setModelFamily(MODELS);
        forecast.setObservedAt(Instant.now());
        forecast.setValidFor(targetDate);
        forecast.setPredictedValueF(mean(membersF));
        forecast.setMemberValuesF(membersF.toArray(new BigDecimal[0]));
        forecast.setRawPayload(rawJson);
        return forecast;
    }

    private static int indexOfDate(JsonNode timeArray, LocalDate targetDate) {
        String target = targetDate.toString();
        for (int i = 0; i < timeArray.size(); i++) {
            if (target.equals(timeArray.get(i).asText())) {
                return i;
            }
        }
        return -1;
    }

    private static List<BigDecimal> extractMembersInFahrenheit(JsonNode daily, int dateIndex) {
        List<BigDecimal> membersF = new ArrayList<>();
        Iterator<String> fieldNames = daily.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!field.contains("member")) {
                continue; // skip "time" and any non-member summary field
            }
            JsonNode valueNode = daily.path(field).path(dateIndex);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                continue; // a model can be short a member for a given run; skip rather than fail the whole pull
            }
            membersF.add(celsiusToFahrenheit(valueNode.decimalValue()));
        }
        return membersF;
    }

    private static BigDecimal celsiusToFahrenheit(BigDecimal celsius) {
        return celsius.multiply(BigDecimal.valueOf(9))
                .divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP)
                .add(BigDecimal.valueOf(32));
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private JsonNode parse(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse Open-Meteo response", e);
        }
    }
}
