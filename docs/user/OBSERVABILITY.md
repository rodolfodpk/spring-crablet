# Observability

Crablet exposes metrics via Micrometer and ships a pre-built Prometheus + Grafana stack for local development and reference.

## Quick Start

**1. Add three dependencies to your app:**

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-metrics-micrometer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**2. Expose the Prometheus endpoint:**

```properties
management.endpoints.web.exposure.include=health,info,prometheus
```

That's all. `MicrometerMetricsCollector` auto-configures when a `MeterRegistry` bean is present — no component scanning needed.

**3. Run the local observability stack** (Prometheus + Grafana pre-loaded with the Crablet dashboard):

```bash
make start          # wallet-example-app on :8080
cd observability
docker compose up -d
open http://localhost:3000   # Grafana — admin / admin
```

Prometheus is at `http://localhost:9090` → Status → Targets to confirm scrape is `UP`.

## Metrics Reference

### EventStore

| Micrometer name | Tags | Description |
|---|---|---|
| `eventstore.events.appended` | — | Events appended |
| `eventstore.events.by_type` | `event_type` | Events appended per type |
| `eventstore.concurrency.violations` | — | DCB optimistic lock conflicts |

### Commands

| Micrometer name | Tags | Description |
|---|---|---|
| `commands.inflight` | `command_type` | Commands currently executing (gauge) |
| `eventstore.commands.duration` | `command_type`, `operation_type` | Execution time (timer) |
| `eventstore.commands.total` | `command_type`, `operation_type` | Commands completed |
| `eventstore.commands.failed` | `command_type`, `error_type` | Commands failed |
| `eventstore.commands.idempotent` | `command_type` | Duplicate/idempotent commands |

### Views

| Micrometer name | Tags | Description |
|---|---|---|
| `views.projection.duration` | `view` | Projection batch duration (timer) |
| `views.events.projected` | `view` | Events projected |
| `views.projection.errors` | `view` | Projection errors |

### Outbox

| Micrometer name | Tags | Description |
|---|---|---|
| `outbox.events.published` | `publisher` | Events published externally |
| `outbox.publishing.duration` | `publisher` | Publishing latency (timer) |
| `outbox.processing.cycles` | — | Outbox polling cycles |
| `outbox.errors` | `publisher` | Publishing errors |

### Automations

| Micrometer name | Tags | Description |
|---|---|---|
| `automations.execution.duration` | `automation` | Batch execution duration (timer) |
| `automations.events.processed` | `automation` | Events processed |
| `automations.execution.errors` | `automation` | Errors |

### Poller / Leader Election

| Micrometer name | Tags | Description |
|---|---|---|
| `processor.is_leader` | `processor`, `instance_id` | 1 = leader, 0 = follower |
| `poller.processing.cycles` | `processor`, `instance_id` | Poll cycles |
| `poller.events.fetched` | `processor`, `instance_id` | Events fetched per cycle |
| `poller.empty.polls` | `processor`, `instance_id` | Empty poll cycles |
| `poller.backoff.active` | `processor`, `instance_id` | Backoff state (gauge) |
| `poller.backoff.empty_poll_count` | `processor`, `instance_id` | Consecutive empty polls (gauge) |

## Histogram Metrics (P95 panels)

Timer metrics emit histograms only when explicitly enabled. Add to `application.properties`:

```properties
management.metrics.distribution.percentiles-histogram.eventstore.commands.duration=true
management.metrics.distribution.percentiles-histogram.views.projection.duration=true
management.metrics.distribution.percentiles-histogram.outbox.publishing.duration=true
management.metrics.distribution.percentiles-histogram.automations.execution.duration=true
```

This is required for `histogram_quantile()` PromQL in Grafana P95 panels.

## Grafana Dashboard

The pre-built dashboard (`observability/grafana/dashboards/crablet-dashboard.json`) covers:

| Section | Panels |
|---|---|
| EventStore | Events appended rate, events by type, DCB concurrency violations |
| Commands | Throughput, P95 duration, in-flight count, failure rate |
| Views | Events projected rate, P95 projection duration, error rate |
| Poller | Leader status, events fetched, empty poll ratio, backoff active |
| Outbox | Events published rate, P95 publishing duration, error rate |
| Automations | Events processed rate, P95 execution duration, error rate |

Import it into any Grafana instance and point Prometheus at your app's `/actuator/prometheus`.

## Key PromQL Queries

```promql
# DCB concurrency violation rate
rate(eventstore_concurrency_violations_total[1m])

# Command P95 latency
histogram_quantile(0.95, rate(eventstore_commands_duration_seconds_bucket[1m]))

# Current outbox leader
processor_is_leader{processor="outbox"} == 1

# Outbox error rate by publisher
rate(outbox_errors_total[1m])

# View projection P95
histogram_quantile(0.95, rate(views_projection_duration_seconds_bucket[1m]))
```

## Alerting

```promql
# High DCB concurrency violations (> 10% of operations)
rate(eventstore_concurrency_violations_total[1m]) > 5

# Outbox sustained errors
rate(outbox_errors_total[1m]) > 0.1

# Frequent leadership changes (instability)
changes(processor_is_leader[5m]) > 0
```

## Opt Out

```properties
crablet.metrics.enabled=false
```

## See Also

- [`crablet-metrics-micrometer/README.md`](../../crablet-metrics-micrometer/README.md) — full module reference and customization
- [`observability/README.md`](../../observability/README.md) — Docker Compose stack quickstart
- [`crablet-eventstore/docs/METRICS.md`](../../crablet-eventstore/docs/METRICS.md) — EventStore metric details and DCB guidance
- [`crablet-outbox/docs/OUTBOX_METRICS.md`](../../crablet-outbox/docs/OUTBOX_METRICS.md) — Outbox metric details
