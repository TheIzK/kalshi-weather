-- Two additional weather subjects, chosen for regime diversity rather than volume: the
-- existing 5 (NYC/CHI/ATL/MIA/LAX) already carry real signal-calibration data, but Atlanta
-- and Miami share the same Southeast synoptic drivers, and all 12 calendar days of trade
-- history so far come from one likely weather regime. Denver (Mountain West, high-altitude,
-- semi-arid) and Seattle (Pacific Northwest, marine) are climate regimes none of the existing
-- 5 represent, chosen to accumulate genuinely independent calibration samples faster rather
-- than more correlated ones. Verified live against Kalshi and NWS on 2026-08-26 — same method
-- as V7: series ticker existence + open markets confirmed via GET /markets?series_ticker=X,
-- station identity confirmed via the *market's* rules_primary text (not the /series endpoint's
-- settlement_sources field, which turned out to read "The Weather Company" even for the
-- already-running NWS-verified subjects — not authoritative, don't trust it for future
-- additions either).
--
--   KXHIGHDEN  -> "Denver (CLIDEN)"  -> Denver International Airport (KDEN) — CLI product
--                 issued by NWS Denver/Boulder (KBOU), matching gridId BOU.
--   KXHIGHTSEA -> "Seattle (CLISEA)" -> Seattle-Tacoma International Airport (KSEA) — CLI
--                 product explicitly names "SEATTLE-TACOMA WA AIRPORT", issued by NWS Seattle
--                 (KSEW), matching gridId SEW. Ticker includes the "T" (KXHIGHTSEA, not
--                 KXHIGHSEA) — confirmed live, the T-less ticker has zero open markets.
-- Station lat/lon from api.weather.gov/stations/{ICAO}; nws_grid_id/x/y from
-- api.weather.gov/points/{lat},{lon} against that same station coordinate.
INSERT INTO subject_stations (subject_key, series_ticker, nws_grid_id, nws_grid_x, nws_grid_y, latitude, longitude)
VALUES
    ('DEN', 'KXHIGHDEN',  'BOU', 74,  66, 39.84658, -104.65622),
    ('SEA', 'KXHIGHTSEA', 'SEW', 124, 60, 47.44472, -122.31361);
