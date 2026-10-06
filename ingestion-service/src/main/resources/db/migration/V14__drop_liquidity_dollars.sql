-- Kalshi removed liquidity_dollars from the markets API response entirely around
-- 2026-10-01 (confirmed against a live GET /markets?series_ticker=... call: the field is
-- absent, not just zero) -- every weather ingestion cycle since then failed outright on the
-- NOT NULL constraint, with zero new weather signals generated for 5 days. The field was
-- already known-unreliable (verified 2026-08-11: always read 0.0000 regardless of real
-- activity) and was never read anywhere outside the entity itself -- open_interest and
-- volume_24h are the real, populated fillability signals already in use. Dropping rather
-- than just relaxing the NOT NULL constraint since there's no reason to keep writing a
-- column that no longer has a data source and was never actually used.
ALTER TABLE markets DROP COLUMN liquidity_dollars;
