-- Audit trail for every weather market evaluation, not just the ones that clear today's
-- thresholds. Market rows are overwritten every ingestion cycle, so until now only markets
-- that actually cleared the live SignalConfig got a permanent price snapshot (frozen inside
-- their signals row) -- every market we looked at but didn't trade left zero trace once the
-- next cycle overwrote it. That's a real selection-bias gap: we can never evaluate "would a
-- different rule have caught trades we skipped" without it. MLB already solved this for
-- itself (mlb_win_probability_snapshots records a prediction for every evaluated game,
-- traded or not) -- this is the weather equivalent. Weather-only (forecast_id NOT NULL);
-- MLB keeps its own richer audit trail rather than also writing here.
CREATE TABLE market_snapshots (
    id                          UUID                     PRIMARY KEY,
    market_id                   VARCHAR(64)              NOT NULL REFERENCES markets (id),
    observed_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    yes_bid                     NUMERIC(6,4)             NOT NULL,
    yes_ask                     NUMERIC(6,4)             NOT NULL,
    no_bid                      NUMERIC(6,4)             NOT NULL,
    no_ask                      NUMERIC(6,4)             NOT NULL,
    open_interest               NUMERIC(14,4),
    fillable                    BOOLEAN                  NOT NULL,
    model_probability           NUMERIC(6,5)             NOT NULL,
    market_implied_probability  NUMERIC(6,5)             NOT NULL,
    edge_percent                NUMERIC(6,3)             NOT NULL,
    net_edge_percent            NUMERIC(6,3)             NOT NULL,
    direction                   VARCHAR(20)              NOT NULL,
    forecast_id                 UUID                     NOT NULL REFERENCES ensemble_forecasts (id),
    resulted_in_signal_id       UUID                     REFERENCES signals (id)
);
CREATE INDEX idx_market_snapshots_market_id ON market_snapshots (market_id);
CREATE INDEX idx_market_snapshots_observed_at ON market_snapshots (observed_at);
