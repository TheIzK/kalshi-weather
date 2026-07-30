CREATE TABLE subject_stations (
    subject_key     VARCHAR(32)  PRIMARY KEY,
    series_ticker   VARCHAR(32)  NOT NULL UNIQUE,
    nws_grid_id     VARCHAR(10)  NOT NULL,
    nws_grid_x      INTEGER      NOT NULL,
    nws_grid_y      INTEGER      NOT NULL,
    latitude        NUMERIC(8,5) NOT NULL,
    longitude       NUMERIC(8,5) NOT NULL
);

CREATE TABLE markets (
    id                  VARCHAR(64)   PRIMARY KEY,
    event_ticker        VARCHAR(64)   NOT NULL,
    series_ticker       VARCHAR(32)   NOT NULL,
    strike_type         VARCHAR(20)   NOT NULL,
    floor_strike        NUMERIC(6,2),
    cap_strike          NUMERIC(6,2),
    occurrence_date     DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    yes_bid             NUMERIC(6,4)  NOT NULL,
    yes_ask             NUMERIC(6,4)  NOT NULL,
    no_bid              NUMERIC(6,4)  NOT NULL,
    no_ask              NUMERIC(6,4)  NOT NULL,
    liquidity_dollars   NUMERIC(14,4) NOT NULL
);
CREATE INDEX idx_markets_series_ticker_occurrence_date
    ON markets (series_ticker, occurrence_date);

CREATE TABLE ensemble_forecasts (
    id                          UUID                     PRIMARY KEY,
    subject_key                 VARCHAR(32)              NOT NULL
        REFERENCES subject_stations (subject_key),
    source                      VARCHAR(64)              NOT NULL,
    model_family                VARCHAR(64),
    observed_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_for                   DATE                     NOT NULL,
    predicted_value_f           NUMERIC(6,2)             NOT NULL,
    member_values_f             NUMERIC[],
    precip_probability_percent  NUMERIC(6,3),
    raw_payload                 JSONB                    NOT NULL
);
CREATE INDEX idx_ensemble_forecasts_subject_valid_for
    ON ensemble_forecasts (subject_key, valid_for);

CREATE TABLE signal_configs (
    id                        UUID         PRIMARY KEY,
    threshold_mode            VARCHAR(20)  NOT NULL,
    flat_threshold_percent    NUMERIC(6,3),
    min_net_edge_after_fees   NUMERIC(6,3),
    min_z_score                NUMERIC(5,3)
);

CREATE TABLE signals (
    id                            UUID                     PRIMARY KEY,
    market_id                     VARCHAR(64)              NOT NULL
        REFERENCES markets (id),
    computed_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    model_probability             NUMERIC(6,5)             NOT NULL,
    market_implied_probability    NUMERIC(6,5)             NOT NULL,
    edge_percent                  NUMERIC(6,3)             NOT NULL,
    net_edge_percent              NUMERIC(6,3)             NOT NULL,
    direction                     VARCHAR(20)              NOT NULL,
    forecast_id                   UUID                     NOT NULL
        REFERENCES ensemble_forecasts (id),
    config_id                     UUID                     NOT NULL
        REFERENCES signal_configs (id),
    status                        VARCHAR(20)              NOT NULL
);
CREATE INDEX idx_signals_market_id ON signals (market_id);
CREATE INDEX idx_signals_status ON signals (status);

CREATE TABLE paper_trades (
    id            UUID                     PRIMARY KEY,
    signal_id     UUID                     NOT NULL REFERENCES signals (id),
    market_id     VARCHAR(64)              NOT NULL REFERENCES markets (id),
    side          VARCHAR(20)              NOT NULL,
    entry_price   NUMERIC(6,4)             NOT NULL,
    quantity      INTEGER                  NOT NULL,
    opened_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at     TIMESTAMP WITH TIME ZONE,
    exit_price    NUMERIC(6,4),
    pnl           NUMERIC(14,4),
    status        VARCHAR(20)              NOT NULL
);
CREATE INDEX idx_paper_trades_status ON paper_trades (status);
