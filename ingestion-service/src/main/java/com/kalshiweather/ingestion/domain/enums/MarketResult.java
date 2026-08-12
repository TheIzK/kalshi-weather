package com.kalshiweather.ingestion.domain.enums;

/** A resolved market's outcome. Null on the market entity until {@code status} is RESOLVED. */
public enum MarketResult {
    YES,
    NO
}
