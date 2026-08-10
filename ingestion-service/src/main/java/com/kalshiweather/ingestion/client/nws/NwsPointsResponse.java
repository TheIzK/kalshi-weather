package com.kalshiweather.ingestion.client.nws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response from GET /points/{lat},{lon} — a one-time lookup per station, not per cycle. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NwsPointsResponse(@JsonProperty("properties") Properties properties) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            @JsonProperty("gridId") String gridId,
            @JsonProperty("gridX") int gridX,
            @JsonProperty("gridY") int gridY
    ) {
    }
}
