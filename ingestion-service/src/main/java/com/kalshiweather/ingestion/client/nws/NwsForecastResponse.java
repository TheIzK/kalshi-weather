package com.kalshiweather.ingestion.client.nws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Response from GET /gridpoints/{office}/{x},{y}/forecast. Temperatures are in °F ({@code units=us}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NwsForecastResponse(@JsonProperty("properties") Properties properties) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(@JsonProperty("periods") List<Period> periods) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(
            @JsonProperty("startTime") OffsetDateTime startTime,
            @JsonProperty("isDaytime") boolean isDaytime,
            @JsonProperty("temperature") BigDecimal temperature,
            @JsonProperty("temperatureUnit") String temperatureUnit,
            @JsonProperty("probabilityOfPrecipitation") Precipitation probabilityOfPrecipitation
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Precipitation(@JsonProperty("value") BigDecimal value) {
    }
}
