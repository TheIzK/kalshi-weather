-- Guardrail, not a model fix: weather trades where the model's stated probability for the
-- side actually taken was under 50% ("long-shot" value bets, e.g. buying YES at 15% because
-- the market priced it even cheaper) went 0-for-45 across 28 independent city-days
-- (2026-08-15 through 2026-08-26). That's too short a calendar span (one likely weather
-- regime, not several) to trust a recalibrated ensemble probability curve, but long enough to
-- stop taking this specific shape of bet while more data accumulates. Reversible: set back to
-- NULL once there's enough regime diversity to actually validate the tail behavior.
ALTER TABLE signal_configs ADD COLUMN min_model_confidence_percent NUMERIC(5,2);

UPDATE signal_configs SET min_model_confidence_percent = 50.00;
