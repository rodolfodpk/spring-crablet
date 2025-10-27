# Crablet Outbox

Transactional outbox implementation for reliable event publishing with Spring Boot integration.

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
- **Management API**: REST API for monitoring and controlling outbox operations

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-outbox</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore
- Spring Boot Web, JDBC
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
    public void publish(String topic, List<StoredEvent> events) throws PublishException {
        // Publish events to Kafka
        for (StoredEvent event : events) {
            kafkaTemplate.send(topic, event);
        }
    }
    
    @Override
    public String getName() {
        return "KafkaPublisher";
    }
}
```

### 3. Events Are Automatically Published

Your domain code doesn't need to change - events are automatically picked up and published:

```java
// Append events with tags
List<Tag> tags = List.of(
    new Tag("wallet-id", "wallet-123"),
    new Tag("event-type", "deposit")
);
eventStore.append(tags, events);

// Outbox processor will automatically publish them to configured publishers
```

## Architecture

### Per-Publisher Schedulers

Each (topic, publisher) pair gets its own independent scheduler, providing:

- **Isolation**: One publisher failure doesn't affect others
- **Flexible Polling**: Per-publisher polling intervals with global fallback
- **Simpler Debugging**: Clear scheduler boundaries and error tracking

The outbox uses a global leader lock to coordinate processing across multiple instances:

- **One leader** processes all publishers on all topics
- **Automatic failover** when leader goes down (PostgreSQL advisory locks release on crash)
- **No configuration needed** - leader election is transparent

## Configuration

```properties
# Enable/disable outbox
crablet.outbox.enabled=true

# Global polling interval (milliseconds)
# Override per-publisher if needed
crablet.outbox.polling-interval-ms=1000

# Topic configuration with per-publisher polling
crablet.outbox.topics.default.publishers=KafkaPublisher,LogPublisher

# Optional: Per-publisher configuration
crablet.outbox.topics.default.publisher-configs[0].name=KafkaPublisher
crablet.outbox.topics.default.publisher-configs[0].polling-interval-ms=500
crablet.outbox.topics.default.publisher-configs[1].name=LogPublisher
crablet.outbox.topics.default.publisher-configs[1].polling-interval-ms=2000
```

## Management API

The outbox provides a management API for monitoring and control:

```bash
# Get outbox status
curl http://localhost:8080/actuator/outbox/status

# Get publisher statistics
curl http://localhost:8080/actuator/outbox/publishers

# Reset publisher position
curl -X POST http://localhost:8080/actuator/outbox/publishers/LogPublisher/reset
```

## Monitoring

Metrics are automatically exposed via Micrometer:

- `crablet.outbox.events.published` - Total events published
- `crablet.outbox.events.failed` - Failed publish attempts
- `crablet.outbox.polling.duration` - Polling duration
- `crablet.outbox.leader.heartbeat` - Leader heartbeat timestamp

## Documentation

- [Outbox Pattern](docs/OUTBOX_PATTERN.md) - Detailed explanation of the transactional outbox pattern
- [Outbox Rationale](docs/OUTBOX_RATIONALE.md) - Why we chose the outbox pattern
- [Outbox Metrics](docs/OUTBOX_METRICS.md) - Metrics reference

## License

MIT

