package com.kalshiweather.ingestion.client.kalshi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Only the fields the domain model actually needs — Kalshi's market object has 30+ fields
 * (title, rules, volume, etc.) we don't care about. Note {@code series_ticker} is NOT
 * present on the wire; the caller already knows it since it was the query filter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KalshiMarketDto(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("event_ticker") String eventTicker,
        @JsonProperty("strike_type") String strikeType,
        @JsonProperty("floor_strike") BigDecimal floorStrike,
        @JsonProperty("cap_strike") BigDecimal capStrike,
        @JsonProperty("occurrence_datetime") Instant occurrenceDatetime,
        @JsonProperty("status") String status,
        @JsonProperty("yes_bid_dollars") BigDecimal yesBidDollars,
        @JsonProperty("yes_ask_dollars") BigDecimal yesAskDollars,
        @JsonProperty("no_bid_dollars") BigDecimal noBidDollars,
        @JsonProperty("no_ask_dollars") BigDecimal noAskDollars,
        @JsonProperty("liquidity_dollars") BigDecimal liquidityDollars,
        @JsonProperty("volume_24h_fp") BigDecimal volume24h,
        @JsonProperty("open_interest_fp") BigDecimal openInterest
) {
}
