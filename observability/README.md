# Crablet Observability Stack

Prometheus + Grafana pre-configured to scrape the `wallet-example-app` and display a Crablet-specific dashboard.

## Prerequisites

- Docker and Docker Compose
- `wallet-example-app` running on port `8080` (see [`make start`](../docs/BUILD.md))

## Quick Start

```bash
# 1. Start the wallet app (in a separate terminal)
make start

# 2. Run one wallet command to materialize lazy counters
# Open http://localhost:8080/swagger-ui.html → POST /api/commands with an OpenWalletCommand

# 3. Start Prometheus + Grafana
cd observability
docker compose up -d

# 4. Open Grafana
open http://localhost:3000   # admin / admin
```

The **Crablet Overview** dashboard is pre-loaded under Dashboards → Browse.

Prometheus is available at http://localhost:9090 — check **Status → Targets** to confirm the scrape target is `UP`.

## Linux Users

Inside Docker, `host.docker.internal` does not resolve by default on Linux. Either:

**Option A** — add `extra_hosts` to the `prometheus` service in `docker-compose.yml`:
```yaml
services:
  prometheus:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

**Option B** — replace `host.docker.internal:8080` in `prometheus/prometheus.yml` with your host IP (e.g. `172.17.0.1:8080`).

## Stopping

```bash
docker compose down
```

## What the Dashboard Shows

| Section | Panels |
|---------|--------|
| EventStore | events appended rate, events by type, DCB concurrency violations |
| Commands | throughput, P95 duration, in-flight count, failure rate |
| Views | events projected rate, P95 projection duration, error rate |
| Poller | leader status, events fetched, empty poll ratio, backoff active |
| Outbox | events published rate, P95 publishing duration, error rate |
| Automations | events processed rate, P95 execution duration, error rate |

P95 duration panels require histogram buckets, which the wallet app enables via:
```properties
management.metrics.distribution.percentiles-histogram.eventstore.commands.duration=true
# (and similar for views, outbox, automations)
```

## Adding Metrics to Your Own App

See [`crablet-metrics-micrometer/README.md`](../crablet-metrics-micrometer/README.md) for the three-dependency setup.
Point your Prometheus at your app's `/actuator/prometheus` endpoint and import `grafana/dashboards/crablet-dashboard.json`.
