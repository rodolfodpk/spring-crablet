# Crablet Outbox

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_outbox)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light framework component for transactional outbox event publishing with DCB event sourcing and Spring Boot integration.

## Positioning

`crablet-outbox` is a poller-backed add-on.

It should not be part of the first adoption promise. Start with the command side, then add outbox when the event model is stable and you need reliable external publication. For learning, one application instance is the recommended setup. For production, default to one application instance per cluster when outbox is enabled.

## Start Here

- Use `crablet-eventstore` and `crablet-commands` first
- Add outbox only after your event model is stable and you need external publication
- In this README, focus on `Quick Start`, `Configuration`, and `Operations`

## Overview

Crablet Outbox provides a robust transactional outbox implementation, ensuring that events are reliably published from your application to external systems without compromising transactional integrity.

## Features

- **Transactional Guarantees**: Events written in the same transaction as your domain events
- **Multiple Publishers**: Support for multiple publisher implementations per topic
- **Per-Publisher Schedulers**: Independent scheduler per (topic, publisher) for isolation and flexible polling
- **Leader Election**: Distributed leader election with global lock strategy
- **Heartbeat Monitoring**: Automatic detection of stale leaders
- **Metrics**: Comprehensive metrics for monitoring outbox health
- **Resilience**: Circuit breakers and retries for reliable publishing
- **Spring Integration**: Ready-to-use Spring Boot components and configuration
- **Operations**: Built on the shared processor management infrastructure

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-outbox</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore (required)
- crablet-event-poller (required) - generic poller infrastructure
- Spring Boot Web
- Spring Boot JDBC
- Resilience4j (for circuit breakers and retries)
- Micrometer (for metrics)
- PostgreSQL JDBC Driver

## Quick Start

### 1. Configure Publishers

```properties
# application.properties
crablet.outbox.enabled=true
crablet.outbox.polling-interval=PT5S
crablet.outbox.topics.default.publishers=LogPublisher,CountDownLatchPublisher
```

### 2. Implement a Publisher

```java
@Component
public class KafkaPublisher implements OutboxPublisher {
    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        // Publish events to Kafka in one batch
        for (StoredEvent event : events) {
            kafkaTemplate.send("default", event);
        }
    }
    
    @Override
    public String getName() {
        return "KafkaPublisher";
    }

    @Override
    public boolean isHealthy() {
        return kafkaTemplate.isHealthy();
    }
}
```

### 3. Events Are Automatically Published

Your domain code doesn't need to change - events are automatically picked up and published:

```java
// Append events with tags
List<AppendEvent> events = List.of(
    AppendEvent.builder("DepositMade")
        .tag("wallet-id", "wallet-123")
        .tag("event-type", "deposit")
        .data(depositData)
        .build()
);
eventStore.appendCommutative(events);

// Outbox processor will automatically publish them to configured publishers
```

## Architecture

Crablet Outbox uses:
- **Per-publisher schedulers**: Independent scheduler per (topic, publisher) pair for isolation and flexible polling
- **Global leader election**: PostgreSQL advisory locks for automatic failover. See [Leader Election Guide](../docs/LEADER_ELECTION.md) for details.
- **At-least-once delivery**: Events may be published multiple times (idempotent consumers required)

**Recommended deployment:**
- **1 application instance per cluster**: The default documented topology

Additional replicas do not increase throughput for the same `(topic, publisher)` processors because leader election keeps only one active leader per processor set.

**Scalability:** Optimized for 1-50 topic/publisher pairs.

For detailed architecture, deployment guidance, scalability limits, and trade-offs, see:
- **[Outbox Pattern](docs/OUTBOX_PATTERN.md)** - Complete architecture and deployment guide
- **[Outbox Rationale](docs/OUTBOX_RATIONALE.md)** - Why we chose the outbox pattern
- **[Outbox Metrics](docs/OUTBOX_METRICS.md)** - Metrics reference

## Configuration

```properties
# Enable/disable outbox
crablet.outbox.enabled=true

# Global polling interval (milliseconds)
# Override per-publisher if needed
crablet.outbox.polling-interval-ms=1000

# Batch size: number of events to scan/publish per cycle
# Default: 100
crablet.outbox.batch-size=100

# Max retries for failed publish attempts
# Default: 3
crablet.outbox.max-retries=3

# Retry delay between failed attempts (milliseconds)
# Default: 5000
crablet.outbox.retry-delay-ms=5000

# Exponential backoff for idle publishers
# Reduces polling when no events are pending
crablet.outbox.backoff.enabled=true
crablet.outbox.backoff.threshold=3              # Empty polls before backoff starts
crablet.outbox.backoff.multiplier=2             # Exponential factor (2^n)
crablet.outbox.backoff.max-seconds=60           # Max backoff duration

# Topic configuration with per-publisher polling
crablet.outbox.topics.default.publishers=KafkaPublisher,LogPublisher

# Optional: Per-publisher configuration
crablet.outbox.topics.default.publisher-configs[0].name=KafkaPublisher
crablet.outbox.topics.default.publisher-configs[0].polling-interval-ms=500
crablet.outbox.topics.default.publisher-configs[1].name=LogPublisher
crablet.outbox.topics.default.publisher-configs[1].polling-interval-ms=2000
```

`crablet.outbox.*` is the global config for the outbox module. It supplies defaults for all outbox processors.

Outbox processors still run per `(topic, publisher)` pair. That means the global outbox config defines module-wide defaults, while topic and publisher config define the per-poller-instance behavior.

## Metrics

Outbox components support metrics collection via Spring's `ApplicationEventPublisher`:

- **Metrics are enabled by default**: Spring Boot automatically provides an `ApplicationEventPublisher` bean
- **Required parameter**: The `eventPublisher` parameter is required in all outbox component constructors
- **Automatic metrics collection**: See [crablet-metrics-micrometer](../crablet-metrics-micrometer/README.md) for automatic metrics collection

The following components publish metrics:
- `OutboxPublishingServiceImpl` - Publishing metrics
- `OutboxProcessorImpl` - Processing cycle metrics
- `OutboxLeaderElector` - Leadership metrics

The following metrics are published:
- `EventsPublishedMetric` - Events published successfully
- `PublishingDurationMetric` - Time taken to publish events
- `OutboxErrorMetric` - Publishing errors
- `ProcessingCycleMetric` - Processing cycle completion
- `LeadershipMetric` - Leader election changes

## Operations

Outbox publishers are managed through the shared processor infrastructure.

The built-in framework management API is `/api/processors`.

If your application wants outbox-specific operational endpoints, it can expose friendlier routes that delegate to the shared management service.

For example, `wallet-example-app` exposes its outbox operations under `/api/outbox`:

```bash
# Get all publisher statuses
curl http://localhost:8080/api/outbox/status

# Get one publisher status
curl http://localhost:8080/api/outbox/default/publishers/LogPublisher/status

# Reset one topic/publisher pair
curl -X POST http://localhost:8080/api/outbox/default/publishers/LogPublisher/reset
```

For the built-in framework API, see [Management API](../docs/MANAGEMENT_API.md).

## Monitoring

Metrics are automatically exposed via Micrometer:

- `crablet.outbox.events.published` - Total events published
- `crablet.outbox.events.failed` - Failed publish attempts
- `crablet.outbox.polling.duration` - Polling duration
- `crablet.outbox.leader.heartbeat` - Leader heartbeat timestamp

## License

MIT
