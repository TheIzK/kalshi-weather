-- Replaces "clears a threshold computed against our own model's self-reported probability"
-- with "has a demonstrated track record of actually beating the market" as the condition for
-- taking a trade. Prompted directly by the finding that clearing the existing edge/confidence
-- guardrails is necessary but not sufficient: the market is still better calibrated (lower
-- Brier score) than our model on every bucket checked so far, in both weather and MLB, even
-- after three rounds of strike-type/direction guardrails -- the earlier "MLB has proven edge"
-- conclusion (found via a cruder win-rate-vs-market check) didn't hold up once checked
-- properly: the model and the market turned out to be biased in opposite directions by
-- similar magnitudes, which the old check couldn't distinguish from genuine edge.
--
-- bucket_calibrations is recomputed in full daily by BucketCalibrationService from every
-- closed, ground-truthed trade -- one row per (domain, strike_type, direction). The live
-- trading path looks up the bucket a candidate trade would fall into and only proceeds if
-- that bucket has enough sample size and model_brier < market_brier. No row for a bucket
-- means no evidence yet, which fails closed (doesn't trade) -- matches the intent of "only
-- trade where we have a good chance," not "trade unless proven bad."
CREATE TABLE bucket_calibrations (
    id            UUID                     PRIMARY KEY,
    domain        VARCHAR(32)              NOT NULL,
    strike_type   VARCHAR(20),
    direction     VARCHAR(20)              NOT NULL,
    sample_size   INTEGER                  NOT NULL,
    model_brier   NUMERIC(6,4)             NOT NULL,
    market_brier  NUMERIC(6,4)             NOT NULL,
    computed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (domain, strike_type, direction)
);

ALTER TABLE signal_configs ADD COLUMN require_proven_calibration BOOLEAN;
ALTER TABLE signal_configs ADD COLUMN min_calibration_sample_size INTEGER;

UPDATE signal_configs SET require_proven_calibration = true;
