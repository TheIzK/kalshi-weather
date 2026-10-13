-- Ground-truth actual daily-high temperatures, pulled from Kalshi's own settled-market
-- history rather than a new weather-data integration. Every settled Kalshi weather market
-- carries expiration_value -- the literal observed high The Weather Company reported for
-- that city/day (verified live 2026-10-13: three different NYC bins for the same day all
-- independently report the same expiration_value, consistent with their individual results).
-- This closes the "we can't measure real forecast accuracy" gap flagged earlier without
-- needing the NWS-observations integration that gap was originally scoped to require --
-- KalshiClient already calls this exact endpoint every cycle, just never with
-- status=settled. One row per subject/day; source is carried explicitly in case a second
-- ground-truth source (e.g. real NWS observations) is ever added alongside this one.
CREATE TABLE actual_observations (
    id               UUID                     PRIMARY KEY,
    subject_key      VARCHAR(32)              NOT NULL REFERENCES subject_stations (subject_key),
    occurrence_date  DATE                     NOT NULL,
    actual_high_f    NUMERIC(6,2)             NOT NULL,
    source           VARCHAR(32)              NOT NULL,
    fetched_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (subject_key, occurrence_date, source)
);
CREATE INDEX idx_actual_observations_subject_date ON actual_observations (subject_key, occurrence_date);
