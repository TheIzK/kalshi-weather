-- Starting threshold only — the design doc is explicit the real profitable threshold, if
-- one exists, should be discovered empirically via walk-forward backtesting, not assumed.
-- 3% net edge after fees is a placeholder to make the mechanism runnable end to end.
INSERT INTO signal_configs (id, threshold_mode, min_net_edge_after_fees)
VALUES (gen_random_uuid(), 'FEE_ADJUSTED', 3.000);
