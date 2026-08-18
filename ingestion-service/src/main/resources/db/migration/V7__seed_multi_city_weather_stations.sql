-- Four additional weather subjects, verified live against Kalshi and NWS on 2026-08-17.
--
-- Each city's settlement station comes from the live rules_primary text on an open Kalshi
-- market in that series (GET /markets?series_ticker=X), matching the pattern established for
-- KXHIGHNY/Central Park — not assumed from "nearest airport":
--   KXHIGHCHI  -> "Chicago (CLIMDW)"  -> Chicago Midway Airport (NOT O'Hare/CLIORD, which is
--                 also a valid NWS CLI station but is not the one Kalshi cites)
--   KXHIGHTATL -> "Atlanta (CLIATL)"  -> Hartsfield-Jackson Atlanta International Airport
--   KXHIGHMIA  -> "Miami (CLIMIA)"    -> Miami International Airport
--   KXHIGHLAX  -> "Los Angeles (CLILAX)" -> Los Angeles International Airport
-- CLI station identity cross-checked against live NWS CLI text products (api.weather.gov
-- /products/types/CLI/locations/{MDW,ATL,MIA,LAX}), each of which names the airport in its
-- header. Station lat/lon pulled from api.weather.gov/stations/{ICAO}; nws_grid_id/x/y from
-- api.weather.gov/points/{lat},{lon} against that same station coordinate (gridId matches the
-- CLI product's issuing office in every case: LOT/FFC/MFL/LOX).
INSERT INTO subject_stations (subject_key, series_ticker, nws_grid_id, nws_grid_x, nws_grid_y, latitude, longitude)
VALUES
    ('CHI', 'KXHIGHCHI',   'LOT', 72,  69, 41.78417, -87.75528),
    ('ATL', 'KXHIGHTATL',  'FFC', 50,  82, 33.64028, -84.42694),
    ('MIA', 'KXHIGHMIA',   'MFL', 105, 51, 25.79056, -80.31639),
    ('LAX', 'KXHIGHLAX',   'LOX', 149, 41, 33.93806, -118.38889);
