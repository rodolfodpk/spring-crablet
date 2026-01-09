# EventStore Metrics

## Available Metrics

EventStore provides metrics for monitoring event operations and DCB (Dynamic Consistency Boundary) concurrency violations.

**Note:** Command execution metrics (command duration, failures, idempotency) are published by `CommandExecutor` (in `crablet-command` module), not by EventStore. See [Command README](../crablet-command/README.md#metrics) for command metrics documentation.

### Event Processing

**Event Counters:**
- `eventstore.events.appended` - Total events appended to store
- `eventstore.events.by_type{event_type}` - Events appended by type

### Concurrency

**Concurrency Violations:**
- `eventstore.concurrency.violations` - Total DCB concurrency violations (optimistic locking failures)

## Prometheus Queries

```promql
# Events appended per second
rate(eventstore.events.appended[1m])

# Events by type distribution
rate(eventstore.events.by_type[1m])

# Concurrency violation rate
rate(eventstore.concurrency.violations[1m])
```

## Use Cases

### Operational Monitoring

**Track throughput:**
```promql
# Events per second
rate(eventstore.events.appended[1m])
```

**Monitor concurrency:**
```promql
# Concurrency violation rate
rate(eventstore.concurrency.violations[1m])
```

### Performance Analysis

**Event processing:**
```promql
# Events per second by type
rate(eventstore.events.by_type[1m])
```

### DCB Monitoring

**Concurrency conflicts:**
```promql
# Concurrency violations indicate retries needed
rate(eventstore.concurrency.violations[1m])
```

High violation rates indicate:
- ❌ **Too many retries needed** - Service is under heavy concurrent load
- ❌ **DCB boundaries too wide** - Decision model includes too many events
- ❌ **Slow operations** - Operations take too long, increasing conflict window

**Healthy rate:** `< 0.01 violations per operation` (1% or less)  
**Unhealthy rate:** `> 0.10 violations per operation` (10% or more indicates problems)

## Alerting

### Recommended Alerts

**High concurrency violation rate:**
```promql
rate(eventstore.concurrency.violations[1m]) > 5
```

**Low throughput:**
```promql
rate(eventstore.events.appended[1m]) < 0.1
```

## Configuration

### Enable Metrics in Spring Boot

Add to `application.properties`:

```properties
# Enable Micrometer metrics
management.endpoints.web.exposure.include=metrics
management.metrics.export.prometheus.enabled=true
```

**Note:** Metrics are automatically collected when `crablet-metrics-micrometer` is on the classpath. No additional bean configuration is required.

## Troubleshooting

### High Concurrency Violations

**Symptoms:**
- `eventstore.concurrency.violations` increasing rapidly
- Many retries happening

**Likely causes:**
1. Wide decision models (too many events affecting decisions)
2. Slow operations
3. High concurrency on same entities

**Solutions:**
1. Narrow decision models - reduce events in Query
2. Optimize slow operations
3. Consider partitioning by entity ID

## Dashboards

### Grafana Panel Suggestions

1. **Events** - Bar chart showing events by type
2. **DCB Health** - Gauge showing concurrency violations (alert if >1%)
3. **Throughput** - Line chart showing events/second

## Comparison: EventStore vs Outbox

| Metric | EventStore | Outbox |
|--------|-----------|--------|
| **Purpose** | Event storage | Event publishing |
| **Throughput** | Events appended | Events published |
| **Concurrency** | DCB violations | - |
| **Leadership** | - | Leader election |
| **Lag** | - | Outbox lag |

EventStore metrics focus on **event storage** (appending events with DCB concurrency control), while Outbox metrics focus on **publish path** (events → external systems).

## See Also

- [DCB Explained](./DCB_AND_CRABLET.md) - Understanding DCB violations
- [Command Metrics](../crablet-command/README.md#metrics) - Command execution metrics
- [Testing](../../TESTING.md) - Testing with metrics
