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
