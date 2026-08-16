-- Generic provenance for non-weather signals. forecast_id was a NOT NULL FK to
-- ensemble_forecasts -- a weather-specific constraint baked into the schema itself.
-- Weather keeps using forecast_id exactly as before (untouched, still populated); MLB
-- (and future NFL/CFB) use source_type/source_reference_id instead, since a generic
-- string pair means no new nullable FK column is needed for every future sport.
ALTER TABLE signals ALTER COLUMN forecast_id DROP NOT NULL;

-- Only weather markets have a threshold shape (GREATER/LESS/BETWEEN); MLB's per-team
-- moneyline markets have no equivalent (wire value "structured", unmapped -> null).
ALTER TABLE markets ALTER COLUMN strike_type DROP NOT NULL;
ALTER TABLE signals ADD COLUMN source_type VARCHAR(20);
ALTER TABLE signals ADD COLUMN source_reference_id VARCHAR(64);
UPDATE signals SET source_type = 'WEATHER_ENSEMBLE' WHERE source_type IS NULL;

CREATE TABLE mlb_teams (
    id              SMALLINT PRIMARY KEY,     -- statsapi team id, e.g. 147 = Yankees
    abbreviation    VARCHAR(5) NOT NULL,
    name            VARCHAR(64) NOT NULL,
    league_id       SMALLINT NOT NULL         -- 103 = AL, 104 = NL
);

-- Seeded once here rather than fetched at runtime -- team metadata (id/abbreviation/
-- league) essentially never changes mid-season, same reasoning as V2's subject_stations seed.
INSERT INTO mlb_teams (id, abbreviation, name, league_id) VALUES
    (108, 'LAA', 'Los Angeles Angels', 103),
    (109, 'AZ',  'Arizona Diamondbacks', 104),
    (110, 'BAL', 'Baltimore Orioles', 103),
    (111, 'BOS', 'Boston Red Sox', 103),
    (112, 'CHC', 'Chicago Cubs', 104),
    (113, 'CIN', 'Cincinnati Reds', 104),
    (114, 'CLE', 'Cleveland Guardians', 103),
    (115, 'COL', 'Colorado Rockies', 104),
    (116, 'DET', 'Detroit Tigers', 103),
    (117, 'HOU', 'Houston Astros', 103),
    (118, 'KC',  'Kansas City Royals', 103),
    (119, 'LAD', 'Los Angeles Dodgers', 104),
    (120, 'WSH', 'Washington Nationals', 104),
    (121, 'NYM', 'New York Mets', 104),
    (133, 'OAK', 'Athletics', 103),
    (134, 'PIT', 'Pittsburgh Pirates', 104),
    (135, 'SD',  'San Diego Padres', 104),
    (136, 'SEA', 'Seattle Mariners', 103),
    (137, 'SF',  'San Francisco Giants', 104),
    (138, 'STL', 'St. Louis Cardinals', 104),
    (139, 'TB',  'Tampa Bay Rays', 103),
    (140, 'TEX', 'Texas Rangers', 103),
    (141, 'TOR', 'Toronto Blue Jays', 103),
    (142, 'MIN', 'Minnesota Twins', 103),
    (143, 'PHI', 'Philadelphia Phillies', 104),
    (144, 'ATL', 'Atlanta Braves', 104),
    (145, 'CWS', 'Chicago White Sox', 103),
    (146, 'MIA', 'Miami Marlins', 104),
    (147, 'NYY', 'New York Yankees', 103),
    (158, 'MIL', 'Milwaukee Brewers', 104);

CREATE TABLE mlb_games (
    game_pk               BIGINT PRIMARY KEY,   -- statsapi gamePk, natural key
    game_date             DATE NOT NULL,
    game_datetime_utc     TIMESTAMPTZ NOT NULL,
    home_team_id          SMALLINT NOT NULL REFERENCES mlb_teams(id),
    away_team_id          SMALLINT NOT NULL REFERENCES mlb_teams(id),
    status                VARCHAR(32) NOT NULL,     -- Scheduled, Warmup, In Progress, Final
    game_type             VARCHAR(1) NOT NULL,      -- R, P, S, E
    kalshi_event_ticker   VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mlb_games_date ON mlb_games(game_date);
CREATE INDEX idx_mlb_games_kalshi_ticker ON mlb_games(kalshi_event_ticker);

CREATE TABLE mlb_probable_pitchers (
    id             BIGSERIAL PRIMARY KEY,
    game_pk        BIGINT NOT NULL REFERENCES mlb_games(game_pk),
    team_id        SMALLINT NOT NULL REFERENCES mlb_teams(id),
    player_id      INT NOT NULL,
    player_name    VARCHAR(128) NOT NULL,
    confirmed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(game_pk, team_id)
);

CREATE TABLE mlb_pitcher_season_stats (
    id                   BIGSERIAL PRIMARY KEY,
    player_id            INT NOT NULL,
    season               INT NOT NULL,
    as_of_date           DATE NOT NULL,        -- snapshot date; raw stats shift daily
    innings_pitched      NUMERIC(6,1) NOT NULL,
    era                  NUMERIC(5,2) NOT NULL,
    whip                 NUMERIC(5,3) NOT NULL,
    strikeouts           INT NOT NULL,
    walks                INT NOT NULL,
    hit_batsmen          INT NOT NULL,
    home_runs_allowed    INT NOT NULL,
    fip                  NUMERIC(5,2) NOT NULL,   -- computed by us, not returned by statsapi
    games_started        INT NOT NULL,
    fetched_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(player_id, season, as_of_date)
);

CREATE TABLE mlb_team_standings_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    team_id          SMALLINT NOT NULL REFERENCES mlb_teams(id),
    as_of_date       DATE NOT NULL,
    wins             INT NOT NULL,
    losses           INT NOT NULL,
    home_win_pct     NUMERIC(5,3) NOT NULL,   -- split win% gives us home-field advantage for free
    away_win_pct     NUMERIC(5,3) NOT NULL,
    fetched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(team_id, as_of_date)
);

CREATE TABLE mlb_win_probability_snapshots (
    id                     BIGSERIAL PRIMARY KEY,
    game_pk                BIGINT NOT NULL REFERENCES mlb_games(game_pk),
    computed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    home_win_probability   NUMERIC(5,4) NOT NULL,
    model_version          VARCHAR(24) NOT NULL,   -- e.g. 'v1-log5-pitcher-adj'
    inputs                 JSONB NOT NULL           -- raw feature values, for later backtesting/calibration
);
CREATE INDEX idx_mlb_wp_game ON mlb_win_probability_snapshots(game_pk);
