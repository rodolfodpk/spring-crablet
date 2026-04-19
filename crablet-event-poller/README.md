# Crablet Event Processor

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_event_processor)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Generic event processing infrastructure for polling, leader election, and backoff with Spring Boot integration.

## Positioning

`crablet-event-poller` is infrastructure for poller-backed modules such as views, outbox, and automations.

It is not the first module most users should reach for directly. The recommended adoption path is:

- start with `crablet-eventstore`
- add `crablet-commands`
- only then add poller-backed modules if you need them

For learning, run one application instance. For production, reason about poller-backed modules separately: views, outbox, and automations each create their own event poller with its own scheduler, leader election, and progress table. A distributed deployment can therefore have one active poller per module, not just one active poller for the whole application.

## Start Here

- Most users should not start with this module directly
- Read this when you need to understand poller behavior across views, outbox, or automations
- The highest-signal sections are `Deployment Recommendation`, `Wakeup Notifications`, and `Configuration Model`

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
- **crablet-automations**: For event-driven policies and side effects

## Deployment Recommendation

For modules built on `crablet-event-poller`, the default production recommendation is:

- default to **1 application instance per cluster** running views, outbox, and automations together when you want the simplest correctness-first topology
- if you want operational isolation, run each poller-backed module as its own singleton worker service: one views worker, one outbox worker, and one automations worker

This recommendation is the same whether you deploy with Docker Compose, Kubernetes, ECS, Nomad, or plain VMs.

Important:

- each module has its own poller: `crablet-views`, `crablet-outbox`, and `crablet-automations` do not share one global event poller
- each module poller uses its own leader election key, so different modules can be active on different application instances
- extra replicas of a singleton worker running the same module do **not** increase throughput for that module's same processors
- extra replicas mainly add standby behavior and operational complexity for that module's poller

If you need more throughput, split processor responsibilities, isolate modules into singleton worker services, tune batch sizes, or reduce polling cost; do not assume many replicas of the same module worker will help.

### Module-level pollers

The built-in poller-backed modules wire the infrastructure independently:

| Module | Poller scope | Leader election |
|---|---|---|
| `crablet-views` | view processors | views module lock |
| `crablet-outbox` | `(topic, publisher)` processors | outbox module lock |
| `crablet-automations` | automation processors | automations module lock |

This gives you two normal deployment shapes.

Default combined service:

- one application service instance runs commands/API plus views, outbox, and automations
- each module still has its own module-level poller inside that one process

Isolated singleton workers:

- one command/API deployment, scaled horizontally
- one singleton views worker service, with one elected active views poller
- one singleton outbox worker service, with one elected active outbox poller
- one singleton automations worker service, with one elected active automations poller

The leader election boundary is per module. A views backlog does not require the outbox or automations poller to be idle, and a different pod or VM may hold each module's leadership lock.

### Shared-fetch mode

Shared-fetch is also module-scoped. When enabled for a module, that module uses one shared fetch loop for all processors inside the module:

```properties
crablet.views.shared-fetch.enabled=true
crablet.outbox.shared-fetch.enabled=true
crablet.automations.shared-fetch.enabled=true
```

For example, `crablet.views.shared-fetch.enabled=true` changes the views module from one DB query per view processor to one DB query per views module cycle. It does not combine views, outbox, and automations into one global poller. Each module still keeps its own scheduler, leader election, and progress tracking.

## Wakeup Notifications (PostgreSQL LISTEN/NOTIFY)

By default the poller uses a fixed schedule. Optionally you can enable PostgreSQL
LISTEN/NOTIFY so the poller wakes up immediately when events are appended, reducing
end-to-end latency from the polling interval down to milliseconds.

The feature has two independent sides:

### NOTIFY — event store side

After every successful append the event store calls `pg_notify(channel, payload)` on
the write connection. This is always active — no configuration is required to turn it
on or off. If nobody is LISTENing, Postgres silently discards the notification at
negligible cost.

Tune only when needed:

```properties
# defaults shown — only set these to override
crablet.eventstore.notifications.channel=crablet_events
crablet.eventstore.notifications.payload=events-appended
```

### LISTEN — poller side

To enable wakeup, set a dedicated direct JDBC URL. The poller opens a single persistent
connection on that URL and issues `LISTEN <channel>`. When a notification arrives it
cancels the pending schedule and polls immediately.

```properties
crablet.event-poller.notifications.jdbc-url=jdbc:postgresql://db-host:5432/mydb
crablet.event-poller.notifications.username=app_user
crablet.event-poller.notifications.password=secret
# optional — must match crablet.eventstore.notifications.channel
# crablet.event-poller.notifications.channel=crablet_events
```

When `jdbc-url` is absent the poller falls back to pure scheduled polling — nothing
else needs to change.

### Connection pooler / proxy compatibility

The NOTIFY call is a plain SQL statement and works through any pooler or proxy.
The LISTEN connection must be **direct and persistent**:

| Environment | LISTEN supported |
|---|---|
| PgBouncer **session mode** | ✅ |
| PgBouncer **transaction mode** | ❌ session state required |
| PgCat | ❌ same reason as PgBouncer transaction mode |
| Aurora PostgreSQL (direct connection) | ✅ |
| Aurora via **RDS Proxy** | ❌ RDS Proxy uses transaction-mode pooling |

Always point `jdbc-url` directly at the database host, bypassing any pooler.

### Tuning polling interval with wakeup enabled

When wakeup is active, scheduled polling becomes a safety net rather than the primary
latency mechanism. You can raise the interval significantly:

```properties
crablet.views.polling-interval-ms=30000
crablet.automations.polling-interval-ms=30000
```

Without wakeup, keep the interval at a value that meets your latency requirements (the
default 1 s is reasonable for most cases; backoff handles idle periods automatically).

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
    <artifactId>crablet-event-poller</artifactId>
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

2. **Scheduling**: By default, `EventProcessorImpl` creates a scheduled task per processor that:
   - Checks if this instance is the leader (via `LeaderElector`)
   - Fetches events from the event store (via `EventFetcher`)
   - Handles events (via `EventHandler`)
   - Updates progress (via `ProgressTracker`)

   With shared-fetch enabled, the module uses one scheduled fetch loop for the module and routes fetched events to the processors inside that module.

3. **Leader Election**: Uses PostgreSQL advisory locks to ensure only one instance processes a module's processor set at a time. Each built-in module has its own lock key. See [Leader Election Guide](../docs/LEADER_ELECTION.md) for detailed explanation of how leader election, crash detection, and failover work.

4. **Backoff**: After a threshold of empty polls, the scheduler skips cycles with exponential backoff

5. **Progress Tracking**: Each processor tracks its own position independently in its module-specific progress table such as `view_progress`, `outbox_topic_progress`, or `reaction_progress`

## Configuration Model

Poller-backed modules should normally expose configuration at two levels:

- **global module config**: shared defaults for the whole module
- **per-processor config**: settings or overrides for one poller instance

Examples:

- `crablet-views`: global `ViewsConfig` plus one `ViewSubscription` per view
- `crablet-automations`: global `AutomationsConfig` plus one `AutomationHandler` per automation
- `crablet-outbox`: global `OutboxConfig` plus one resolved processor per `(topic, publisher)` pair

This is important because the engine always runs per processor instance. Even when many processors share the same module defaults, the poller still creates one `ProcessorConfig` per processor.

## Usage

This module is primarily used as infrastructure by other Crablet modules (`crablet-views`, `crablet-outbox`, and `crablet-automations`). However, you can use it directly to build custom event processors.

### Example: Custom Event Processor

```java
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.StreamPosition;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.crablet.eventpoller.EventFetcher;
import com.crablet.eventpoller.EventHandler;
import com.crablet.eventpoller.ProcessorConfig;
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
        this.query = QueryBuilder.builder()
            .matching(new String[]{"MyEventType"}, new Tag("processor_id", "my-processor"))
            .build();
    }
    
    @Override
    public List<StoredEvent> fetchEvents(String processorId, long lastPosition, int batchSize) {
        // Fetch events using EventRepository (or use direct SQL for better performance)
        // Note: EventRepository.query() doesn't support batch size limit, so we fetch and limit manually
        // For production, consider using direct SQL like OutboxEventFetcher/ViewEventFetcher do
        StreamPosition streamPosition = lastPosition > 0 ? StreamPosition.of(lastPosition, Instant.EPOCH, "0") : StreamPosition.zero();
        List<StoredEvent> events = eventRepository.query(query, streamPosition);
        // Limit to batch size
        return events.stream().limit(batchSize).toList();
    }
}

@Component
public class MyEventHandler implements EventHandler<String> {
    @Override
    public int handle(String processorId, List<StoredEvent> events) {
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
# Get all processor statuses
curl http://localhost:8080/api/processors

# Pause a processor
curl -X POST http://localhost:8080/api/processors/my-processor/pause

# Resume a processor
curl -X POST http://localhost:8080/api/processors/my-processor/resume
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
- **[Crablet Automations](../crablet-automations/README.md)** - Uses event processor for policies and side effects
- **[Crablet EventStore](../crablet-eventstore/README.md)** - Core event sourcing library

## License

MIT
