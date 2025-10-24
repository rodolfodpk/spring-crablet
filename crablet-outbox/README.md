# Crablet Outbox

Transactional outbox pattern implementation for reliable event publishing.

## Overview

Crablet Outbox provides a robust implementation of the transactional outbox pattern, ensuring that events are reliably published from your application to external systems without compromising transactional integrity.

## Features

- **Transactional Guarantees**: Events written in the same transaction as your domain events
- **Multiple Publishers**: Support for multiple publisher implementations per topic
- **Leader Election**: Distributed leader election with multiple strategies (GLOBAL, PER_TOPIC_PUBLISHER)
- **Heartbeat Monitoring**: Automatic detection of stale leaders
- **Metrics**: Comprehensive metrics for monitoring outbox health
- **Resilience**: Circuit breakers and retries for reliable publishing

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-outbox</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-core
- Spring Boot JDBC
- Resilience4j (circuit breakers, retries)
- Micrometer (metrics)

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
// Append events normally
eventStore.append("wallet-123", events);

// Outbox processor will automatically publish them
```

## Lock Strategies

### GLOBAL

One leader processes all topics:

```properties
crablet.outbox.lock-strategy=GLOBAL
```

**Use when**: You have a small number of instances and want maximum throughput.

### PER_TOPIC_PUBLISHER

Separate leader per topic/publisher combination:

```properties
crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER
```

**Use when**: You need fine-grained control and have many instances.

## Configuration

```properties
# Enable/disable outbox
crablet.outbox.enabled=true

# Polling interval (ISO-8601 duration)
crablet.outbox.polling-interval=PT5S

# Lock strategy
crablet.outbox.lock-strategy=PER_TOPIC_PUBLISHER

# Heartbeat timeout (ISO-8601 duration)
crablet.outbox.heartbeat-timeout=PT30S

# Topic configuration
crablet.outbox.topics.default.publishers=LogPublisher,YourPublisher
crablet.outbox.topics.default.batch-size=100
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

