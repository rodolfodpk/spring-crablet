# Crablet Event Processor

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_event_processor)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Generic event processing infrastructure for polling, leader election, and backoff with Spring Boot integration.

## Overview

Crablet Event Processor provides a generic, reusable infrastructure for building event-driven processors. It handles the common concerns of event processing:

- **Polling**: Scheduled polling of events from the event store
- **Leader Election**: Distributed leader election using PostgreSQL advisory locks
- **Backoff**: Exponential backoff for idle processors
- **Progress Tracking**: Independent progress tracking per processor instance
- **Error Handling**: Error counting and processor status management
- **Management API**: REST API for monitoring and controlling processors

This module is used by:
- **crablet-outbox**: For publishing events to external systems
- **crablet-views**: For projecting events into materialized read models

## Features

- **Generic Design**: Type-safe, generic processor interface that can be adapted to different use cases
- **Leader Election**: PostgreSQL advisory locks for distributed leader election
- **Exponential Backoff**: Reduces polling frequency when no events are available
- **Progress Tracking**: Independent progress tracking per processor instance
- **Read Replica Support**: Uses read replicas for optimal read performance
- **Management API**: REST endpoints for monitoring and control
- **Metrics**: Event-driven metrics via Spring Events
- **Spring Integration**: Ready-to-use Spring Boot components

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-event-processor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore (required)
- Spring Boot Web (for management API)
- Spring Boot JDBC
- PostgreSQL JDBC Driver

## Architecture

The event processor is built around a few key interfaces:

### Core Interfaces

- **`EventProcessor<T, I>`**: Main processor interface with start/stop/pause/resume operations
- **`EventFetcher<I>`**: Fetches events from the event store (uses read replicas)
- **`EventHandler<I>`**: Handles events (must be idempotent)
- **`ProgressTracker<I>`**: Tracks processing progress per processor instance
- **`LeaderElector`**: Manages distributed leader election
- **`ProcessorConfig<I>`**: Configuration for processor instances

### Key Components

1. **EventProcessorImpl**: Generic implementation that handles:
   - Scheduled polling per processor instance
   - Leader election coordination
   - Backoff state management
   - Error handling and status tracking

2. **LeaderElectorImpl**: PostgreSQL advisory lock-based leader election

3. **BackoffState**: Manages exponential backoff for idle processors

4. **ProgressTracker**: Tracks last processed position per processor

## How It Works

1. **Configuration**: Each processor instance has a `ProcessorConfig` with polling interval, batch size, and backoff settings

2. **Scheduling**: `EventProcessorImpl` creates a scheduled task per processor that:
   - Checks if this instance is the leader (via `LeaderElector`)
   - Fetches events from the event store (via `EventFetcher`)
   - Handles events (via `EventHandler`)
   - Updates progress (via `ProgressTracker`)

3. **Leader Election**: Uses PostgreSQL advisory locks to ensure only one instance processes each processor at a time. See [Leader Election Guide](../LEADER_ELECTION.md) for detailed explanation of how leader election, crash detection, and failover work.

4. **Backoff**: After a threshold of empty polls, the scheduler skips cycles with exponential backoff

5. **Progress Tracking**: Each processor tracks its own position independently in the `processor_progress` table

## Usage

This module is primarily used as infrastructure by other Crablet modules (`crablet-outbox` and `crablet-views`). However, you can use it directly to build custom event processors.

### Example: Custom Event Processor

```java
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.eventprocessor.EventFetcher;
import com.crablet.eventprocessor.EventHandler;
import com.crablet.eventprocessor.ProcessorConfig;
import javax.sql.DataSource;
import java.util.List;

@Component
public class MyEventProcessorConfig implements ProcessorConfig<String> {
    @Override
    public String getProcessorId() {
        return "my-processor";
    }
    
    @Override
    public long getPollingIntervalMs() {
        return 1000; // 1 second
    }
    
    @Override
    public int getBatchSize() {
        return 100;
    }
    
    // ... other config methods
}

@Component
public class MyEventFetcher implements EventFetcher<String> {
    private final EventRepository eventRepository; // For querying events
    private final Query query; // Your event query (e.g., specific event types/tags)
    
    public MyEventFetcher(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
        // Define your query (e.g., fetch specific event types)
        this.query = QueryBuilder.create()
            .matching(new String[]{"MyEventType"}, new Tag("processor_id", "my-processor"))
            .build();
    }
    
    @Override
    public List<StoredEvent> fetchEvents(String processorId, long lastPosition, int batchSize) {
        // Fetch events using EventRepository (or use direct SQL for better performance)
        // Note: EventRepository.query() doesn't support batch size limit, so we fetch and limit manually
        // For production, consider using direct SQL like OutboxEventFetcher/ViewEventFetcher do
        Cursor cursor = lastPosition > 0 ? Cursor.of(SequenceNumber.of(lastPosition)) : Cursor.zero();
        List<StoredEvent> events = eventRepository.query(query, cursor);
        // Limit to batch size
        return events.stream().limit(batchSize).toList();
    }
}

@Component
public class MyEventHandler implements EventHandler<String> {
    @Override
    public int handle(String processorId, List<StoredEvent> events, DataSource writeDataSource) {
        // Process events (must be idempotent!)
        for (StoredEvent event : events) {
            // Handle event
        }
        return events.size();
    }
}

@Configuration
public class ProcessorConfig {
    @Bean
    public EventProcessor<MyEventProcessorConfig, String> eventProcessor(
            Map<String, MyEventProcessorConfig> configs,
            LeaderElector leaderElector,
            ProgressTracker<String> progressTracker,
            MyEventFetcher eventFetcher,
            MyEventHandler eventHandler,
            DataSource writeDataSource,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher eventPublisher) {
        return new EventProcessorImpl<>(
            configs, leaderElector, progressTracker, eventFetcher,
            eventHandler, writeDataSource, taskScheduler, eventPublisher
        );
    }
}
```

## Idempotency Requirements

**Critical**: All `EventHandler` implementations MUST be idempotent. The same event may be processed multiple times if progress tracking fails after successful handling.

### For Outbox Handlers
- Publishers should handle duplicate events gracefully
- External systems (Kafka, webhooks) should be idempotent consumers
- Event position/ID can be used for deduplication

### For View Handlers
- Use idempotent database operations:
  - JOOQ `store()` method (upsert) instead of `insert()`
  - SQL `ON CONFLICT` clauses
  - View tables should have unique constraints on event identifiers

## Transaction Boundaries

Handler execution and progress update are **NOT** in the same transaction. This is intentional:
- If handler succeeds but progress update fails, events will be reprocessed
- This allows handlers to use their own transaction boundaries
- Handlers that need atomicity should manage transactions internally

## Management API

The processor provides a REST API for monitoring and control:

```bash
# Get processor status
curl http://localhost:8080/actuator/processor/status

# Pause a processor
curl -X POST http://localhost:8080/actuator/processor/my-processor/pause

# Resume a processor
curl -X POST http://localhost:8080/actuator/processor/my-processor/resume
```

## Metrics

The processor publishes metrics via Spring Events:

- `ProcessingCycleMetric` - Processing cycle completion
- `LeadershipMetric` - Leader election changes
- `ProcessorMetric` - Processor-specific metrics

See [crablet-metrics-micrometer](../crablet-metrics-micrometer/README.md) for automatic metrics collection.

## See Also

- **[Crablet Outbox](../crablet-outbox/README.md)** - Uses event processor for outbox pattern
- **[Crablet Views](../crablet-views/README.md)** - Uses event processor for view projections
- **[Crablet EventStore](../crablet-eventstore/README.md)** - Core event sourcing library

## License

MIT

