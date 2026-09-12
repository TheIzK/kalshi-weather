-- Guardrail, not a model fix (same pattern as V9's confidence floor): BETWEEN markets (narrow
-- fixed-width temperature bins, e.g. "82-83F") are a persistent structural loser across the
-- full trade history through 2026-09-08 -- $22.30 lost over 466 trades (-4.8c/trade average) --
-- while GREATER/LESS (open-ended tail bins) are net positive (+$1.92 and +$1.65 respectively).
-- Consistent across all 7 cities individually, not concentrated in one location or time window.
-- Likely mechanism: a narrow bin's empirical-CDF probability is far more sensitive to ensemble
-- sampling noise than a wide tail's cumulative probability, given only ~119-122 members.
-- Reversible: set back to NULL/false if this stops holding up as more data accumulates.
ALTER TABLE signal_configs ADD COLUMN exclude_between_strike_type BOOLEAN;

UPDATE signal_configs SET exclude_between_strike_type = true;
