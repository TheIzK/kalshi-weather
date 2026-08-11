-- Kalshi's liquidity_dollars field reads 0.0000 on every KXHIGHNY market regardless of real
-- activity (verified against live data on 2026-08-11: markets with $11k+ 24h volume and
-- 1-cent bid/ask spreads still report liquidity_dollars=0). volume_24h_fp and
-- open_interest_fp are populated and meaningful, so we capture them and use them for the
-- signal-generation fillability gate instead.
ALTER TABLE markets
    ADD COLUMN volume_24h    NUMERIC(14,4) NOT NULL DEFAULT 0,
    ADD COLUMN open_interest NUMERIC(14,4) NOT NULL DEFAULT 0;
