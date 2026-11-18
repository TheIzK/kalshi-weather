-- Guardrail, mirror image of V13's exclude_less_buy_yes: GREATER-strike markets where the
-- model favors BUY_YES (betting on an unusually hot day) found via real Brier-score
-- calibration (model vs market, scored against actual_observations ground truth) once that
-- table had enough history to use. As of 2026-11-18: GREATER+BUY_YES scores a 0.40 Brier
-- (worse than always guessing 50%, which scores 0.25) vs. 0.035 for the market on the same
-- trades. Spans 6 of 7 cities, holds in both halves of the time series -- a real pattern, not
-- noise, though dollar impact so far is small (-$0.73 over 95 trades). Reversible via config,
-- not a model change.
ALTER TABLE signal_configs ADD COLUMN exclude_greater_buy_yes BOOLEAN;

UPDATE signal_configs SET exclude_greater_buy_yes = true;
