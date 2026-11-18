package com.kalshiweather.ingestion.report;

/** Request-only parameter for PerformanceTrendController — not persisted anywhere, so it
 * lives alongside the report feature rather than in domain.enums with the @Enumerated
 * persisted columns. */
public enum TrendGranularity {
    WEEK,
    MONTH
}
