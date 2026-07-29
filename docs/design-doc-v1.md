# Kalshi Weather Signal System — Design Doc v1

Status: domain model and data source mapping validated against real API responses (Kalshi, Open-Meteo Ensemble, NWS) on 2026-07-29. Ready for `ingestion-service` implementation.

## Scope

v1 targets Kalshi weather markets (starting with `KXHIGHNY`, NYC daily high temp) only.
Commodities markets are a deliberate phase-2 stretch goal — Kalshi's commodities contracts
(WTI, Brent, gold, silver) are priced against already-efficient global futures markets and
are much less likely to carry an exploitable edge than thinner weather markets.

## Domain Model

### Market
```
Market {
  id: String                    // Kalshi ticker, e.g. "KXHIGHNY-26JUL30-T84"
  eventTicker: String           // e.g. "KXHIGHNY-26JUL30" — groups strike-bucket markets for one day
  seriesTicker: String          // e.g. "KXHIGHNY" — maps to subjectKey via a station lookup table
  strikeType: StrikeType        // GREATER, LESS, BETWEEN (matches Kalshi's "greater"/"less"/"between")
  floorStrike: BigDecimal?      // present for GREATER and BETWEEN
  capStrike: BigDecimal?        // present for LESS and BETWEEN
  occurrenceDate: LocalDate     // the calendar day this market resolves against (NOT an Instant — see below)
  status: MarketStatus          // OPEN, CLOSED, RESOLVED
  yesBid, yesAsk, noBid, noAsk: BigDecimal   // real quotes, not just last_price
  liquidityDollars: BigDecimal  // used to detect stale/unfillable quotes
}
```
**Field-naming note:** deliberately mirrors Kalshi's own `floor_strike`/`cap_strike` naming
rather than inventing our own — reduces translation bugs at the ingestion boundary.

**Pricing note:** `Signal` generation must compute market-implied probability from a real
fillable price (mid of bid/ask, gated on `liquidityDollars > 0`), not `last_price` — several
real markets pulled during design had `last_price` far from a fillable quote on thin books.

### EnsembleForecast (renamed from `Forecast` — see decision log)
```
EnsembleForecast {
  id: UUID
  subjectKey: String            // station identifier, joins to Market via seriesTicker lookup
  source: String                 // "OPEN_METEO_ENSEMBLE", "NWS"
  modelFamily: String?           // "gfs_seamless", "ecmwf_ifs025", "icon_seamless" (null for NWS)
  observedAt: Instant             // when we pulled it — real timestamp, ingestion-time
  validFor: LocalDate             // calendar day this forecast is FOR — not an Instant (see below)
  predictedValueF: BigDecimal     // point value or ensemble mean, in Fahrenheit (converted at ingestion)
  memberValuesF: BigDecimal[]?    // full ensemble member list in Fahrenheit; null for NWS
  precipProbabilityPercent: BigDecimal?  // native from NWS; null for Open-Meteo unless populated later
  rawPayload: JsonB
}
```

**Why `LocalDate`, not `Instant`, for `validFor`/`occurrenceDate`:** three sources represent
"which day" three different ways — Kalshi's `occurrence_datetime` is an arbitrary UTC anchor,
Open-Meteo's daily array uses plain date strings, NWS periods carry real local-time boundaries
with a day/night split. Joining on `Instant` equality is a bug waiting to happen at a date
boundary. Every source gets normalized to a calendar date at ingestion time; joins happen on
that date, never on exact timestamps.

**Unit conversion:** Open-Meteo returns Celsius; Kalshi strikes are in Fahrenheit. Conversion
happens once, explicitly, at ingestion — never inline elsewhere.

**NWS mapping specifics:** pull the daytime period (`isDaytime: true`) matching the target
calendar date, not just "the next period in the array." `probabilityOfPrecipitation` maps
directly to `precipProbabilityPercent`, no distribution math needed.

**Station lookup:** NWS's `/points/{lat},{lon}` metadata (gridId/gridX/gridY) is a one-time
lookup per station, cached in a small `subjectKey → NWS grid + lat/lon` table — not re-fetched
per ingestion cycle.

### Signal
```
Signal {
  id: UUID
  marketId: String
  computedAt: Instant
  modelProbability: BigDecimal
  marketImpliedProbability: BigDecimal
  edgePercent: BigDecimal              // modelProbability - marketImpliedProbability
  netEdgePercent: BigDecimal           // edgePercent minus estimated Kalshi fee at this price/direction
  direction: SignalDirection           // BUY_YES, BUY_NO
  forecastId: UUID
  configId: UUID                       // which SignalConfig produced this
  status: SignalStatus                 // ACTIVE, EXPIRED, ACTED_ON
}
```

### SignalConfig
```
SignalConfig {
  id: UUID
  thresholdMode: ThresholdMode         // FLAT_PERCENT, FEE_ADJUSTED, CONFIDENCE_ADJUSTED
  flatThresholdPercent: BigDecimal?
  minNetEdgeAfterFees: BigDecimal?
  minZScore: BigDecimal?
}
```
Threshold is deliberately configurable rather than a fixed 3% — the actual profitable
threshold, if one exists, is meant to be discovered empirically via backtesting, not assumed.

### PaperTrade
```
PaperTrade {
  id: UUID
  signalId: UUID
  marketId: String
  side: SignalDirection
  entryPrice: BigDecimal
  quantity: Integer
  openedAt: Instant
  closedAt: Instant?
  exitPrice: BigDecimal?
  pnl: BigDecimal?
  status: TradeStatus                  // OPEN, CLOSED
}
```

## Data Sources

| Source | Auth | Notes |
|---|---|---|
| Kalshi market data | None (public) | `https://api.elections.kalshi.com/trade-api/v2/markets?series_ticker=X&status=open` |
| Open-Meteo Ensemble | None (free) | `https://ensemble-api.open-meteo.com/v1/ensemble` — confirmed ~122 members across GEFS/ECMWF/ICON for one call, 7 days of data per call |
| NWS | None (public) | `/points/{lat},{lon}` (cache once) → `/gridpoints/{office}/{x},{y}/forecast` (per ingestion cycle); **blocks non-browser automated fetching from some clients — verify your ingestion service's User-Agent is set per NWS's API guidance** |

## Probability Methodology

1. **Primary: empirical CDF from ensemble members.** `P(above strike) = fraction of members > strike`.
2. **Fallback: parametric normal fit** (mean/stddev of members → normal CDF) — used only when
   the empirical estimate lands at 0% or 100%, since ~122 members is too few to trust in the
   extreme tails, which is exactly where real edges are most likely to hide.
3. **Precip-type markets** use NWS's native `probabilityOfPrecipitation` directly — no
   distribution fitting needed.
4. **Validated example (real data, 2026-07-29 pull):** NWS's July 30 NYC forecast (77°F) and
   the Open-Meteo ensemble mean for the same day (~80-81°F) disagreed by ~4°F — a concrete,
   real illustration of why single-source models are risky, kept as a first real test case.

## Fee Model

Kalshi taker fee: `round_up(0.07 × C × P × (1-P))`, where P is the fill price in dollars.
Asymmetric as a percentage of stake: buying the cheaper side of a market costs a higher
percentage of your stake in fees than buying the pricier/more-likely side. `netEdgePercent`
must account for this per-trade, not assume a flat fee.

## Key Decisions Log

- Modular services (ingestion / pricing-engine / api-service, possibly a separate Python
  model service) over literal microservices — cost and complexity tradeoff, documented
  reasoning valued over raw service count
- Redis Streams over Kafka/RabbitMQ — cheap, sufficient, doubles as cache
- k3s (not bare Docker Compose) for the "real orchestration" story, once services exist
  and run correctly via Compose first
- Weather markets first; commodities deliberately deferred (efficiency argument above)
- Edge threshold configurable, evaluated empirically via backtesting — not fixed at 3%
- Backtesting must use walk-forward validation (train/calibrate on one window, test on a
  later untouched one) to avoid curve-fitting a threshold to noise

## Build Order From Here

1. Repo skeleton + `docker-compose.yml` (Postgres, Redis) + `CLAUDE.md` (derived from this doc)
2. `ingestion-service` vertical slice — NYC only, all three sources, raw storage, prove the
   pipeline runs end-to-end before building anything downstream
3. Verify against real data manually (query the DB) before writing probability logic
4. Probability calculator / `SignalProvider` interface
5. `api-service`
6. Backtesting engine with walk-forward validation
7. k3s + CI/CD, once services work locally
