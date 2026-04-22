# Crablet Metrics - Micrometer

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_metrics)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Micrometer metrics collector for Crablet event-driven metrics.

## Overview

This module provides automatic metrics collection for Crablet using Micrometer. It subscribes to metric events published via Spring Events and records them to Micrometer.

**Framework-agnostic design**: The core Crablet modules (`crablet-eventstore`, `crablet-commands`, `crablet-outbox`) publish framework-agnostic metric events. This module collects those events and records them to Micrometer.

## Getting Started

Add three dependencies to your application:

```xml
<!-- Crablet metrics collector (auto-configures itself) -->
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-metrics-micrometer</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot Actuator (provides MeterRegistry) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus registry (exposes /actuator/prometheus) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Then expose the Prometheus endpoint in `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
```

That is all. The `MicrometerMetricsCollector` is auto-configured when a `MeterRegistry` bean is present — no component scanning of `com.crablet.metrics.micrometer` is required.

**Note:** This library does not create a `MeterRegistry` bean. Your application must provide one (Spring Boot Actuator does this automatically). If no `MeterRegistry` is present in the context, the collector is simply not registered.

### Enable Histogram Metrics (for P95 panels)

Timers emit histograms when this configuration is added:

```properties
management.metrics.distribution.percentiles-histogram.eventstore.commands.duration=true
management.metrics.distribution.percentiles-histogram.views.projection.duration=true
management.metrics.distribution.percentiles-histogram.outbox.publishing.duration=true
management.metrics.distribution.percentiles-histogram.automations.execution.duration=true
```

This is required for `histogram_quantile()` PromQL queries in Grafana.

### Disable Metrics

To opt out of metric collection:

```properties
crablet.metrics.enabled=false
```

## Metrics Reference

Micrometer names (dot-separated) are shown. Prometheus adds underscores and suffixes (e.g. `_total`, `_seconds_count`).

### EventStore

| Micrometer name | Tags | Description |
|---|---|---|
| `eventstore.events.appended` | — | Events appended to store |
| `eventstore.events.by_type` | `event_type` | Events appended per type |
| `eventstore.concurrency.violations` | — | DCB optimistic lock conflicts |

### Commands

| Micrometer name | Tags | Description |
|---|---|---|
| `commands.inflight` | `command_type` | Commands currently executing (gauge) |
| `eventstore.commands.duration` | `command_type`, `operation_type` | Command execution time (timer) |
| `eventstore.commands.total` | `command_type`, `operation_type` | Commands completed |
| `eventstore.commands.failed` | `command_type`, `error_type` | Commands failed |
| `eventstore.commands.idempotent` | `command_type` | Duplicate/idempotent commands |

### Outbox

| Micrometer name | Tags | Description |
|---|---|---|
| `outbox.events.published` | `publisher` | Events published externally |
| `outbox.publishing.duration` | `publisher` | Publishing latency (timer) |
| `outbox.processing.cycles` | — | Outbox polling cycles |
| `outbox.errors` | `publisher` | Publishing errors |

### Poller / Processor

| Micrometer name | Tags | Description |
|---|---|---|
| `processor.is_leader` | `processor`, `instance_id` | Leader gauge (1=leader, 0=follower) |
| `poller.processing.cycles` | `processor`, `instance_id` | Poll cycles |
| `poller.events.fetched` | `processor`, `instance_id` | Events fetched per cycle |
| `poller.empty.polls` | `processor`, `instance_id` | Empty poll cycles |
| `poller.backoff.active` | `processor`, `instance_id` | Backoff state gauge |
| `poller.backoff.empty_poll_count` | `processor`, `instance_id` | Consecutive empty polls gauge |

### Views

| Micrometer name | Tags | Description |
|---|---|---|
| `views.projection.duration` | `view` | Projection batch duration (timer) |
| `views.events.projected` | `view` | Events projected |
| `views.projection.errors` | `view` | Projection errors |

### Automations

| Micrometer name | Tags | Description |
|---|---|---|
| `automations.execution.duration` | `automation` | Automation batch duration (timer) |
| `automations.events.processed` | `automation` | Events processed |
| `automations.execution.errors` | `automation` | Automation errors |

## Grafana Dashboard

A pre-built dashboard covering all six metric groups is available at:

```
observability/grafana/dashboards/crablet-dashboard.json
```

See `observability/README.md` for how to run the full Prometheus + Grafana stack with `docker compose`.

## How It Works

1. **Event Publishing**: Core modules publish metric events via Spring `ApplicationEventPublisher`
2. **Auto-configuration**: `MicrometerMetricsAutoConfiguration` registers `MicrometerMetricsCollector` when `MeterRegistry` is present
3. **Event Collection**: `MicrometerMetricsCollector` subscribes to metric events using `@EventListener`
4. **Metrics Recording**: Events are converted to Micrometer counters, timers, and gauges

## Customization

- **Override**: Declare your own `MicrometerMetricsCollector` bean — `@ConditionalOnMissingBean` ensures yours takes precedence
- **Disable**: Set `crablet.metrics.enabled=false`
- **Extend**: Create additional `@EventListener` beans that subscribe to the same metric event types

## Breaking Changes

### 1.0.0-SNAPSHOT

- **Label rename**: The leadership gauge previously used tag `instance`; it now uses `instance_id` for consistency with poller metrics. Update any existing Prometheus alerts or Grafana panels that reference `processor_is_leader{instance=...}` to use `processor_is_leader{instance_id=...}`.
- **Metric rename**: `outbox.is_leader` has been replaced by `processor.is_leader` (shared across views, outbox, and automations). Update any references to `outbox_is_leader` in dashboards or alert rules.

## See Also

- [Observability stack](../observability/README.md) — Docker Compose quickstart with Prometheus and Grafana
- [EventStore Metrics](../crablet-eventstore/docs/METRICS.md) — EventStore metric details and PromQL examples
- [Outbox Metrics](../crablet-outbox/docs/OUTBOX_METRICS.md) — Outbox metric details
