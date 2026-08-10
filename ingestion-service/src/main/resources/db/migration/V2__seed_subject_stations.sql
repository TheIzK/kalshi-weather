-- Central Park, NY — matches Kalshi's KXHIGHNY settlement source (NWS Climatological
-- Report for Central Park). Grid values verified against the live NWS /points endpoint
-- on 2026-08-10.
INSERT INTO subject_stations (subject_key, series_ticker, nws_grid_id, nws_grid_x, nws_grid_y, latitude, longitude)
VALUES ('NYC', 'KXHIGHNY', 'OKX', 34, 45, 40.78290, -73.96540);
