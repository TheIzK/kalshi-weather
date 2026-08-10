package com.kalshiweather.ingestion.domain.enums;

/**
 * Mirrors Kalshi's actual {@code status} field values (verified against the live API on
 * 2026-08-10) rather than the "open"/"closed" vocabulary used by the {@code status} query
 * param — the wire value for an open market is literally "active".
 */
public enum MarketStatus {
    ACTIVE,
    CLOSED,
    RESOLVED
}
