# Operations

How to run this locally, how it's deployed, and why the infra decisions were made this way.
Domain model and signal logic live in `docs/design-doc-v1.md` — this file is deployment/ops only.

## Current state

**The DigitalOcean droplet is the single live copy.** It's the only instance that should be
running continuously. Local `docker compose` is for development and manual testing only — spin
`ingestion` up when you need it, don't leave it running in the background. Running both at once
means two independent instances hit the same live APIs and write to two separate, diverging
databases, which is more confusing than useful now that the droplet exists.

## Local development

No local Maven install is assumed — build/test both run inside a throwaway container against
the cached `maven-repo-cache` volume, so first run downloads dependencies and later runs are fast.

Start the datastores (and, optionally, ingestion):
```
docker compose up -d postgres redis        # datastores only — the normal default
docker compose up -d --build ingestion     # add ingestion when you actually want it running
docker compose stop ingestion              # stop it again when done; postgres/redis can stay up
```

Run unit tests:
```
docker run --rm \
  -v "$PWD/ingestion-service:/app" -v maven-repo-cache:/root/.m2 -w /app \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/kalshi_signals \
  maven:3.9-eclipse-temurin-21 \
  mvn -B test -Dtest='!LiveApiSmokeTest,!IngestionOrchestratorLiveTest'
```
(Requires `docker compose up -d postgres` first — tests hit a real Postgres, not an in-memory one.)

Run the live API smoke test (hits real Kalshi/Open-Meteo/NWS — not in CI, run manually after
touching any client code, per its own doc comment):
```
# same docker run as above, with -Dtest=LiveApiSmokeTest
```

Query local data:
```
docker exec kalshi-weather-postgres-1 psql -U kalshi -d kalshi_signals -c "SELECT ..."
```

## Cloud deployment

- **Provider**: DigitalOcean. Droplet `kalshi-weather-ingestion`, region `nyc3` (matches the
  KXHIGHNY/NYC subject), size `s-1vcpu-2gb` ($12/mo) — sized for the JVM + Postgres + Redis
  running together; the $6/mo 1GB tier risked OOM-kills recreating the exact ingestion gaps this
  deployment exists to fix.
- **Image**: `ingestion-service/Dockerfile` (multi-stage: Maven build, slim JRE runtime).
  `docker-compose.yaml` wires it in as a real service with `restart: unless-stopped` — supervised,
  not a hand-run `docker run`, so it survives crashes and reboots. Docker itself is enabled at the
  systemd level on the droplet, so it comes back after a full droplet reboot too.
- **Networking/security**: Postgres and Redis are bound to `127.0.0.1` only in
  `docker-compose.yaml` (not `0.0.0.0`) — on a box with a public IP, publishing them would expose
  default/weak credentials to the whole internet. A DigitalOcean Cloud Firewall on the droplet
  additionally allows only inbound SSH (22); everything else inbound is denied, all outbound is
  allowed (needed for the Kalshi/Open-Meteo/NWS calls out). Defense in depth: either control alone
  would already block external DB access.
- **Provisioning**: done via `doctl` (DigitalOcean's CLI), authenticated locally via
  `doctl auth init` — the API token lives only in the local machine's `~/.config/doctl`, never in
  this repo or in chat.

Redeploy after a code change:
```
git push origin main
ssh root@159.203.139.141 "cd /opt/kalshi-weather && git pull && docker compose up -d --build"
```
The repo is cloned to `/opt/kalshi-weather` on the droplet (public GitHub repo, no deploy
credentials needed). A fresh `git clone` + `docker compose up -d --build` (via cloud-init) is also
exactly how the droplet was originally provisioned from scratch.

Check on it:
```
ssh root@159.203.139.141 "docker ps; docker logs kalshi-ingestion --tail 50"
ssh root@159.203.139.141 "docker exec kalshi-weather-postgres-1 psql -U kalshi -d kalshi_signals -c 'SELECT ...'"
```

**Token-gated internal endpoints** (port 8080, published — not loopback-only, unlike Postgres/
Redis — gated by the `STATUS_API_TOKEN` env var instead, stored only in the droplet's
gitignored `.env`, header `X-Status-Token`):
- `GET /internal/status` — paper-trade counts + latest open/closed, for an external checker
  that can't reach Postgres directly (`StatusController`).
- `POST /internal/mlb/refresh` — manually runs `MlbIngestionOrchestrator.dailyRefresh()`
  (schedule/pitchers/stats/standings) on demand, e.g. after a redeploy that happened past
  7am America/New_York that day, instead of waiting until the next morning
  (`MlbAdminController`).
```
TOKEN=<value, in the droplet's /opt/kalshi-weather/.env>
curl -H "X-Status-Token: $TOKEN" http://159.203.139.141:8080/internal/status
curl -X POST -H "X-Status-Token: $TOKEN" http://159.203.139.141:8080/internal/mlb/refresh
```

## Key decisions

- **Ingestion interval: 15 minutes** (`ingestion.schedule.fixed-delay-ms`). Not the bottleneck —
  Open-Meteo's ensemble and NWS's forecast don't update anywhere near that often, so polling
  faster wouldn't surface new information. Revisit only if settlement/pnl data (see below) ever
  shows entry-price staleness actually costing real edge.
- **Settlement reconciliation: hourly**, separate schedule from ingestion
  (`ingestion.schedule.settlement-check-delay-ms`) — settlement doesn't need 15-minute granularity.
- **Signal dedup**: one `ACTIVE`/`ACTED_ON` signal per market at a time, regardless of direction.
  Before this, the same market got a brand-new duplicate `Signal` row every cycle it still cleared
  threshold. Direction flips (model crossing from favoring YES to favoring NO mid-market) are
  deliberately not handled — the first qualifying signal locks the market until it resolves.
- **Paper trade sizing: fixed 1 contract per trade.** No stake-sizing config exists anywhere in
  `SignalConfig` or the design doc; inventing one wasn't warranted just to open a trade. See
  Future Considerations below.
- **Local vs. droplet split**: see "Current state" above.
- **Multi-domain signals (MLB, and later NFL/CFB)**: extend the existing `Signal`/
  `PaperTrade` pipeline rather than run a parallel tracking system per sport — `Market.id`,
  `SignalDirection`, `PaperTradeService`, and `SettlementReconciliationService` were already
  domain-agnostic. `SignalGenerationService.evaluate(Market, BigDecimal modelProbability,
  String sourceType, String sourceReferenceId)` is the generic entry point: pass your
  computed probability and a `sourceType` label (e.g. `MLB_WIN_PROBABILITY`) plus a
  `sourceReferenceId` pointing at wherever you persisted your own model's audit trail —
  everything downstream (dedup, fee/edge math, paper trading, settlement) just works. Weather
  keeps using the original `evaluate(Market, EnsembleForecast)` overload and the `forecast_id`
  FK; don't migrate it, both are fine to coexist indefinitely.
- **MLB cadence**: 7am America/New_York daily (`mlb.schedule.daily-refresh-cron`) does the
  full refresh — schedule, probable pitchers, season stats, team standings. Hourly
  (`mlb.schedule.hourly-recheck-delay-ms`) just re-checks the schedule for pitcher scratches
  (they move win probability more than almost anything else) and re-evaluates today's games.
  Standings/stats are intentionally not re-fetched hourly — they don't shift within a day.
- **MLB market matching**: Kalshi models one game as two separate per-team binary markets
  sharing an `event_ticker` (verified against live data 2026-08-16), not a single moneyline
  market — e.g. `KXMLBGAME-26AUG191420CWSCHC-CWS` / `-CHC`. Matched by team abbreviation
  (the ticker suffix after the last hyphen) + occurrence date, not by parsing the rest of the
  ticker format, which is an implementation detail of Kalshi's that could change.

## Future considerations (not built, intentionally deferred)

- **Configurable position sizing**, replacing the fixed 1-contract trade — no reason to build this
  until there's an actual sizing strategy to express.
- **Minimum/breakeven trade size calculator**: Kalshi's fee rounds up to the nearest cent per
  trade, so very small trades absorb proportionally more rounding drag than larger ones — there's
  a real minimum size below which a nominally-qualifying edge doesn't actually clear after fees.
  Worth surfacing per-signal once sizing becomes configurable.
- **Backtesting engine with walk-forward validation** — the next real step per
  `docs/design-doc-v1.md`'s build order, once enough settled `paper_trades` history exists to
  make it meaningful.
