-- Bumped by Hibernate's @UpdateTimestamp on every Market save. IngestionOrchestrator saves
-- every currently-open market on every ingestion cycle regardless of whether anything
-- changed, so MAX(updated_at) per series_ticker is a free, accurate "last successful
-- ingestion" signal for the dashboard — no orchestrator changes needed.
ALTER TABLE markets ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
