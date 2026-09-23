-- Guardrail, same pattern as V9/V11: LESS-strike markets where the model favors BUY_YES
-- (betting on an unusually cold day) have been a consistent, near-total loser in the era
-- since the BETWEEN exclusion went fully live (2026-09-12 onward) -- -$2.35 over 25 trades
-- (8.0% win rate), spanning all 7 cities across 9 distinct days, while every other
-- strike-type/direction combination in the same window is net positive. Losing at every
-- confidence level -- including one trade where all 119/119 ensemble members predicted cold
-- and the market still settled warm -- looks like a systematic cold-side bias rather than
-- noise, though the window is short enough that it could still reflect one shared weather
-- regime. Reversible via config, not a model change, while more data accumulates.
ALTER TABLE signal_configs ADD COLUMN exclude_less_buy_yes BOOLEAN;

UPDATE signal_configs SET exclude_less_buy_yes = true;
