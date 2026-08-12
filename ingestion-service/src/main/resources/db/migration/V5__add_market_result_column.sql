-- Nullable: unresolved markets have no result yet. Populated once KalshiClient.fetchMarket
-- observes status=settled (wire value; RESOLVED in our MarketStatus enum).
ALTER TABLE markets ADD COLUMN result VARCHAR(10);
