package com.kalshiweather.ingestion.client.kalshi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarketsResponse(
        @JsonProperty("markets") List<KalshiMarketDto> markets,
        @JsonProperty("cursor") String cursor
) {
}
