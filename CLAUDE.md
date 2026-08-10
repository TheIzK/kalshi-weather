# Kalshi Weather Signal System

Distributed Java/Spring system detecting mispricings between Kalshi weather markets
and a weather-model-derived probability estimate. See `docs/design-doc-v1.md` for the full
domain model, data source mappings, and decision log — this file covers conventions
and current build status only.

## Stack
- Java 21, Spring Boot 3.x, Maven
- Postgres, Redis (Streams)
- Docker Compose locally; k3s later, once services run correctly via Compose

## Current phase
`ingestion-service` now covers the full vertical slice for one subject (NYC / KXHIGHNY):
pulls open Kalshi markets and both weather sources, computes a model probability
(`SignalProvider` — empirical CDF from the Open-Meteo ensemble, normal-fit fallback at the
extremes), and persists a `Signal` when the edge clears the configured `SignalConfig`
threshold. `pricing-engine` and `api-service` as separate services, and the backtesting
engine, are still not built.

## Non-negotiable conventions (see docs/DESIGN.md for why)
- `validFor` / `occurrenceDate` are `LocalDate`, never `Instant` — every source's date
  gets normalized to a calendar day at ingestion time before anything joins on it.
- Open-Meteo returns Celsius; convert to Fahrenheit once, explicitly, at ingestion.
- NWS forecast periods mix day/night — always filter to `isDaytime: true` for the
  target date, never take "the next period in the array."
- NWS requests need a proper `User-Agent` header per their API guidance.
- Market pricing uses bid/ask mid, gated on `liquidityDollars > 0` — never `last_price`
  alone.

## Commands
- Build: `mvn clean install`
- Test: `mvn test`
- Local stack: `docker compose up -d`

## Compact instructions
When compacting, preserve the domain model field names and the "current phase" section —
drop exploratory discussion.
