package com.kalshiweather.ingestion.client.kalshi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response envelope for the single-market endpoint, GET /markets/{ticker}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarketResponse(
        @JsonProperty("market") KalshiMarketDto market
) {
}
