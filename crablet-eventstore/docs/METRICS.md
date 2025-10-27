# EventStore Metrics

## Available Metrics

EventStore provides comprehensive metrics for monitoring command execution, event processing, and DCB (Dynamic Consistency Boundary) operations.

### Command Execution

**Command Counters:**
- `eventstore.commands.total{command_type}` - Total commands processed by type
- `eventstore.commands.failed{command_type,error_type}` - Failed commands with error type
- `eventstore.commands.idempotent{command_type}` - Idempotent operations (duplicate requests handled gracefully)

**Command Timers:**
- `eventstore.commands.duration{command_type}` - Command execution time by type

### Event Processing

**Event Counters:**
- `eventstore.events.appended` - Total events appended to store
- `eventstore.events.by_type{event_type}` - Events appended by type

### Concurrency

**Concurrency Violations:**
- `eventstore.concurrency.violations` - Total DCB concurrency violations (optimistic locking failures)

## Prometheus Queries

```promql
# Command execution rate by type
rate(eventstore.commands.total[1m])

# Failed command rate by type
rate(eventstore.commands.failed[1m])

# Idempotent operation rate
rate(eventstore.commands.idempotent[1m])

# Command execution latency (P95)
histogram_quantile(0.95, rate(eventstore.commands.duration_bucket[5m]))

# Events appended per second
rate(eventstore.events.appended[1m])

# Events by type distribution
rate(eventstore.events.by_type[1m])

# Concurrency violation rate
rate(eventstore.concurrency.violations[1m])

# Failure rate by error type
sum by (error_type) (rate(eventstore.commands.failed[1m]))
```

## Use Cases

### Operational Monitoring

**Track throughput:**
```promql
# Total command rate
sum(rate(eventstore.commands.total[1m]))

# Events per second
rate(eventstore.events.appended[1m])
```

**Monitor errors:**
```promql
# Error rate by command type
sum by (command_type) (rate(eventstore.commands.failed[1m]))

# Total error rate
sum(rate(eventstore.commands.failed[1m]))
```

### Performance Analysis

**Command latency:**
```promql
# P50, P95, P99 latencies
histogram_quantile(0.50, rate(eventstore.commands.duration_bucket[5m]))
histogram_quantile(0.95, rate(eventstore.commands.duration_bucket[5m]))
histogram_quantile(0.99, rate(eventstore.commands.duration_bucket[5m]))
```

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

# Violations vs successful commands ratio
rate(eventstore.concurrency.violations[1m]) / rate(eventstore.commands.total[1m])
```

**Idempotency:**
```promql
# How often are duplicate requests detected?
rate(eventstore.commands.idempotent[1m])

# Idempotency rate by command type
sum by (command_type) (rate(eventstore.commands.idempotent[1m]))
```

## Alerting

### Recommended Alerts

**High error rate:**
```promql
sum(rate(eventstore.commands.failed[1m])) > 10
```

**High concurrency violation rate:**
```promql
rate(eventstore.concurrency.violations[1m]) > 5
```

**High command latency:**
```promql
histogram_quantile(0.95, rate(eventstore.commands.duration_bucket[5m])) > 1s
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

### Bean Configuration

EventStoreMetrics must be registered as a Spring bean:

```java
@Configuration
public class CrabletConfig {
    
    @Bean
    public EventStoreMetrics eventStoreMetrics(MeterRegistry registry) {
        return new EventStoreMetrics(registry);
    }
}
```

## Understanding Metrics

### Commands

**Command Types:**
Common command types in wallet domain:
- `open_wallet` - Wallet creation
- `deposit` - Money deposit
- `withdraw` - Money withdrawal
- `transfer` - Money transfer between wallets

**Error Types:**
- `validation` - Invalid command input
- `concurrency` - DCB concurrency violation (optimistic locking conflict)
- `runtime` - Unexpected runtime error
- `exception` - Exception during execution

### Idempotent Operations

Idempotent operations are duplicate requests detected and handled gracefully:
- Same `withdrawal_id` processed twice → second call returns success without side effects
- Same `wallet_id` creation attempted twice → second call throws `ConcurrencyException`

This metric tracks how often idempotency is being leveraged (good for retry-heavy clients).

### Concurrency Violations

High violation rates indicate:
- ❌ **Too many retries needed** - Service is under heavy concurrent load
- ❌ **DCB boundaries too wide** - Decision model includes too many events
- ❌ **Slow command handlers** - Commands take too long, increasing conflict window

**Healthy rate:** `< 0.01 violations per command` (1% or less)
**Unhealthy rate:** `> 0.10 violations per command` (10% or more indicates problems)

## Troubleshooting

### High Concurrency Violations

**Symptoms:**
- `eventstore.concurrency.violations` increasing rapidly
- Many retries happening

**Likely causes:**
1. Wide decision models (too many events affecting decisions)
2. Slow command handlers
3. High concurrency on same aggregates

**Solutions:**
1. Narrow decision models - reduce events in Query
2. Optimize slow command handlers
3. Consider partitioning by aggregate ID

### High Error Rates

**Check error types:**
```promql
sum by (error_type, command_type) (rate(eventstore.commands.failed[1m]))
```

**Common issues:**
- `validation` - Bad input from clients
- `concurrency` - Too many conflicts (see above)
- `runtime` - Bug in command handlers

### Low Idempotency Rate

If idempotency rate is low but you expect duplicates:

**Likely causes:**
1. Clients not sending unique IDs (e.g., missing `withdrawal_id`)
2. Idempotency checks not implemented in handlers

**Solution:**
- Add unique IDs to commands
- Implement `withIdempotencyCheck()` in AppendCondition

## Dashboards

### Grafana Panel Suggestions

1. **Command Rate** - Line chart showing commands/second by type
2. **Error Rate** - Stacked area showing failed commands by error type
3. **Latency** - Heatmap showing p50/p95/p99 latencies
4. **Events** - Bar chart showing events by type
5. **DCB Health** - Gauge showing concurrency violations (alert if >1%)
6. **Idempotency Usage** - Gauge showing idempotent rate (indicates retry patterns)

## Comparison: EventStore vs Outbox

| Metric | EventStore | Outbox |
|--------|-----------|--------|
| **Purpose** | Command execution | Event publishing |
| **Throughput** | Commands processed | Events published |
| **Latency** | Command duration | Publishing duration |
| **Errors** | Command failures | Publishing failures |
| **Idempotency** | Duplicate commands | - |
| **Concurrency** | DCB violations | - |
| **Leadership** | - | Leader election |
| **Lag** | - | Outbox lag |

EventStore metrics focus on **write path** (commands → events), while Outbox metrics focus on **publish path** (events → external systems).

## See Also

- [DCB Explained](./DCB_AND_CRABLET.md) - Understanding DCB violations
- [Command Handling](../../GETTING_STARTED.md#command-handling) - Writing command handlers
- [Testing](../../TESTING.md) - Testing with metrics

