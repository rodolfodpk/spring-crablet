# Crablet Views

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_views)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light framework component for asynchronous view projections using materialized read models with Spring Boot integration.

## Overview

Crablet Views provides a complete solution for building materialized read models from event streams:

- **Asynchronous Projections**: Events are processed asynchronously using the generic event processor infrastructure
- **Base Classes**: `AbstractTypedViewProjector` (for sealed interfaces) and `AbstractViewProjector` (non-generic) provide transaction support, automatic deserialization, and error handling
- **Transaction Support**: Each batch of events is processed atomically - all succeed or all roll back
- **Tag-Based Subscriptions**: Subscribe to events by type and/or tags
- **Independent Progress Tracking**: Each view tracks its own processing progress independently
- **Flexible Database Access**: Use JdbcTemplate (recommended), Spring Data JDBC, JOOQ, or any database access technology
- **Idempotent Operations**: Built-in support for at-least-once processing semantics
- **Spring Integration**: Ready-to-use Spring Boot components and auto-configuration
- **Management API**: REST API for monitoring and controlling view projections

## Features

- **ViewProjector Interface**: Simple interface for projecting events into materialized views
- **Base Classes**: `AbstractTypedViewProjector` and `AbstractViewProjector` provide transaction support, error handling, and reduce boilerplate
- **Transaction Support**: Automatic transaction wrapping for batch atomicity (all events succeed or all roll back)
- **Event Subscription**: Flexible subscription configuration by event type and/or tags
- **Progress Tracking**: Independent progress tracking per view in `view_progress` table
- **Leader Election**: Distributed leader election for high availability
- **Backoff Strategy**: Exponential backoff for error handling
- **REST API**: Built-in REST endpoints for view management
- **Spring Auto-Configuration**: Zero-configuration setup when enabled

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-views</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-eventstore (required)
- crablet-event-processor (required)
- Spring Boot Web (for REST API)
- Spring Boot JDBC (for database access)
- PostgreSQL JDBC Driver

**Note:** You can use any database access technology you prefer (JdbcTemplate, Spring Data JDBC, JOOQ, etc.). JdbcTemplate is recommended as the default for its simplicity and excellent PostgreSQL support.

## Quick Start

### 1. Enable Views

```properties
# application.properties
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100
```

### 2. Create View Table

Create your view table using Flyway migrations:

```sql
-- V1__create_wallet_view.sql
CREATE TABLE wallet_view (
    wallet_id VARCHAR(255) PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 3. Implement ViewProjector

**Recommended: Using AbstractTypedViewProjector (with Sealed Interfaces)**

This is the recommended approach when using sealed interfaces for type-safe event handling:

```java
@Component
public class WalletViewProjector extends AbstractTypedViewProjector<WalletEvent> {
    
    public WalletViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }
    
    @Override
    public String getViewName() {
        return "wallet-view";
    }
    
    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }
    
    @Override
    protected boolean handleEvent(WalletEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
        return switch (event) {
            case WalletOpened opened -> {
                jdbc.update("""
                    INSERT INTO wallet_view (wallet_id, balance, last_updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (wallet_id) 
                    DO UPDATE SET 
                        balance = EXCLUDED.balance,
                        last_updated_at = EXCLUDED.last_updated_at
                    """,
                    opened.walletId(),
                    BigDecimal.valueOf(opened.initialBalance()),
                    Timestamp.from(clockProvider.now())
                );
                yield true;
            }
            case DepositMade deposit -> {
                jdbc.update("""
                    UPDATE wallet_view
                    SET balance = ?, last_updated_at = ?
                    WHERE wallet_id = ?
                    """,
                    BigDecimal.valueOf(deposit.newBalance()),
                    Timestamp.from(clockProvider.now()),
                    deposit.walletId()
                );
                yield true;
            }
            default -> false; // Ignore other event types
        };
    }
}
```

**Benefits:**
- ✅ Automatic transaction support (all events in batch processed atomically)
- ✅ Automatic deserialization to typed events
- ✅ Type-safe pattern matching with sealed interfaces
- ✅ Built-in error handling and logging
- ✅ ClockProvider for testability

**Alternative: Using AbstractViewProjector (Non-Generic)**

For simple cases without sealed interfaces:

```java
@Component
public class SimpleViewProjector extends AbstractViewProjector {
    
    public SimpleViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }
    
    @Override
    public String getViewName() {
        return "simple-view";
    }
    
    @Override
    protected boolean handleEvent(StoredEvent event, JdbcTemplate jdbc) {
        // Manual deserialization if needed
        if ("SomeEvent".equals(event.type())) {
            SomeEvent data = deserialize(event, SomeEvent.class);
            // ... handle event
            return true;
        }
        return false;
    }
}
```

**Alternative: Direct ViewProjector Implementation**

For maximum flexibility, implement `ViewProjector` directly:

```java
@Component
public class WalletViewProjector implements ViewProjector {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    public WalletViewProjector(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getViewName() {
        return "wallet-view";
    }
    
    @Override
    public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) {
        // Manual implementation - no transaction support or base class features
        // ...
    }
}
```

**Alternative: Using Spring Data JDBC**

```java
@Component
public class WalletViewProjector implements ViewProjector {
    
    private final WalletViewRepository repository;
    private final ObjectMapper objectMapper;
    
    public WalletViewProjector(WalletViewRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }
    
    // Note: Spring Data JDBC doesn't natively support PostgreSQL ON CONFLICT,
    // so you may need to use @Query with custom SQL for idempotent upserts
    // or use JdbcTemplate for those operations
    
    // ... implementation
}
```

**Alternative: Using JOOQ**

```java
@Component
public class WalletViewProjector implements ViewProjector {
    
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    
    public WalletViewProjector(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }
    
    // Note: Requires JOOQ code generation setup
    // ... implementation using JOOQ DSL
}
```

### 4. Configure Subscription

Create a `ViewSubscriptionConfig` bean to subscribe to events:

```java
@Configuration
public class ViewConfiguration {
    
    @Bean
    public ViewSubscriptionConfig walletViewSubscription() {
        return ViewSubscriptionConfig.builder("wallet-view")
            .eventTypes("WalletOpened", "DepositMade", "WithdrawalMade", "TransferCompleted")
            .requiredTags("wallet-id")  // Only process events with wallet-id tag
            .build();
    }
}
```

### 5. Views Are Automatically Processed

The view processor automatically:
- Polls events from the event store
- Filters by subscription configuration (event types and tags)
- Calls your `ViewProjector` implementations
- Tracks progress independently per view
- Handles errors with exponential backoff

## Architecture

Crablet Views uses the generic event processor infrastructure:

- **Event Fetcher**: Fetches events from read replica based on subscription filters
- **Event Handler**: Delegates to user-provided `ViewProjector` implementations
- **Progress Tracker**: Tracks processing progress per view in `view_progress` table
- **Leader Election**: Uses PostgreSQL advisory locks for distributed leader election. See [Leader Election Guide](../LEADER_ELECTION.md) for details.
- **Backoff Strategy**: Exponential backoff for error recovery

**Recommended deployment:** 
- **1 instance**: Works fine in Kubernetes (auto-restart on crash, brief downtime)
- **2+ instances**: Recommended for zero-downtime failover (follower takes over within 5-30 seconds)

## Configuration

### Application Properties

```properties
# Enable views
crablet.views.enabled=true

# Polling interval in milliseconds
crablet.views.polling-interval-ms=1000

# Batch size for event processing
crablet.views.batch-size=100

# Backoff configuration
crablet.views.backoff-threshold=10
crablet.views.backoff-multiplier=2
crablet.views.max-backoff-seconds=60

# Leader election retry interval
crablet.views.leader-election-retry-interval-ms=30000
```

### Subscription Configuration

Subscriptions can be configured programmatically via `ViewSubscriptionConfig` beans:

```java
@Bean
public ViewSubscriptionConfig myViewSubscription() {
    return ViewSubscriptionConfig.builder("my-view")
        .eventTypes("EventType1", "EventType2")
        .requiredTags("tag-key1", "tag-key2")  // ALL tags must be present
        .anyOfTags("tag-key3", "tag-key4")     // At least ONE tag must be present
        .build();
}
```

## Base Classes

The module provides base classes to reduce boilerplate and provide common functionality:

### AbstractTypedViewProjector (Recommended)

For projects using sealed interfaces (e.g., `WalletEvent`, `CourseEvent`):

```java
@Component
public class WalletViewProjector extends AbstractTypedViewProjector<WalletEvent> {
    
    public WalletViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }
    
    @Override
    public String getViewName() {
        return "wallet-view";
    }
    
    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }
    
    @Override
    protected boolean handleEvent(WalletEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
        return switch (event) {
            case WalletOpened opened -> handleWalletOpened(opened, jdbc);
            case DepositMade deposit -> handleDepositMade(deposit, jdbc);
            // ... other event types
        };
    }
}
```

**Benefits:**
- ✅ Automatic transaction support (batch atomicity)
- ✅ Automatic deserialization to typed events
- ✅ Type-safe pattern matching
- ✅ Built-in error handling and logging
- ✅ ClockProvider for testability

### AbstractViewProjector

For projects without sealed interfaces or when you need more control:

```java
@Component
public class SimpleViewProjector extends AbstractViewProjector {
    
    public SimpleViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }
    
    @Override
    public String getViewName() {
        return "simple-view";
    }
    
    @Override
    protected boolean handleEvent(StoredEvent event, JdbcTemplate jdbc) {
        // Manual deserialization if needed
        if ("SomeEvent".equals(event.type())) {
            SomeEvent data = deserialize(event, SomeEvent.class);
            // ... handle event
            return true;
        }
        return false;
    }
}
```

**Benefits:**
- ✅ Automatic transaction support (batch atomicity)
- ✅ Built-in error handling and logging
- ✅ ClockProvider for testability
- ✅ Works with any event structure

## Idempotency and Transactions

View projectors **MUST** use idempotent operations since events may be processed multiple times due to at-least-once semantics.

**Transaction Support:**
- Each batch of events is processed within a single transaction using `TransactionTemplate`
- Transaction support is built into the base class - no need to add `@Transactional` annotations
- If any event in the batch fails, the entire batch is rolled back automatically
- This ensures atomicity: all events succeed or all fail together
- Progress tracking happens **after** the transaction commits (separate transaction)
- If progress update fails, events may be reprocessed (hence idempotency requirement)

**Transaction Configuration:**
- Propagation: `REQUIRED` (joins existing transaction if present, creates new if not)
- Isolation: `READ_COMMITTED` (database default)
- Rollback: Automatic on any exception

**Recommended patterns:**
- Use PostgreSQL `ON CONFLICT` clauses (works great with JdbcTemplate)
- Use upsert operations
- Use idempotent updates (e.g., set `new_balance` from event instead of accumulating)

**Example with JdbcTemplate:**
```java
writeJdbc.update("""
    INSERT INTO wallet_view (wallet_id, balance, last_updated_at)
    VALUES (?, ?, ?)
    ON CONFLICT (wallet_id) 
    DO UPDATE SET 
        balance = EXCLUDED.balance,
        last_updated_at = EXCLUDED.last_updated_at
    """,
    walletId, balance, timestamp
);
```

## Transaction Management

View projectors automatically process each batch within a transaction:
- **Batch size**: Configurable via `crablet.views.batch-size` (default: 100)
- **Transaction scope**: All events in a batch are processed atomically
- **Rollback behavior**: If any event fails, the entire batch is rolled back automatically
- **Implementation**: Uses Spring's `TransactionTemplate` for explicit transaction management
- **Isolation level**: `READ_COMMITTED` (configurable via TransactionTemplate)

**Important:**
- Progress tracking happens in a **separate transaction** after handler execution
- This design allows handlers to manage their own transaction boundaries
- If progress update fails, events may be reprocessed (idempotency required)
- Transaction support is built into `AbstractWalletViewProjector` - no additional configuration needed

## Management API

The views module provides a REST API for managing view projections:

```bash
# Get view status
GET /api/views/{viewName}/status

# Pause view processing
POST /api/views/{viewName}/pause

# Resume view processing
POST /api/views/{viewName}/resume

# Reset failed view
POST /api/views/{viewName}/reset
```

**Example:**
```bash
curl http://localhost:8080/api/views/wallet-view/status
# Response: {"viewName":"wallet-view","status":"ACTIVE","lag":0}
```

## Progress Tracking

Each view tracks its own progress independently in the `view_progress` table:

- `view_name`: Unique identifier for the view
- `last_position`: Last processed event position
- `status`: Current status (ACTIVE, PAUSED, FAILED)
- `error_count`: Number of consecutive errors
- `last_error`: Last error message
- `instance_id`: Instance processing this view (for leader election)

## Tag Filtering

Tags are stored in PostgreSQL as `"key=value"` format. Subscription filters support:

- **Required Tags**: ALL specified tags must be present
- **AnyOf Tags**: At least ONE of the specified tags must be present
- **Event Types**: Filter by event type names

**Example:**
```java
ViewSubscriptionConfig.builder("course-view")
    .eventTypes("CourseDefined", "StudentSubscribedToCourse")
    .requiredTags("course_id")          // Must have course_id tag
    .anyOfTags("region-us", "region-eu") // Must have at least one region tag
    .build();
```

## Error Handling

Views use exponential backoff for error recovery:

1. On error, the view records the error in `view_progress`
2. After `backoff-threshold` consecutive errors, backoff starts
3. Polling interval increases exponentially (up to `max-backoff-seconds`)
4. After successful processing, backoff resets

Failed views can be reset via the management API or by clearing the error count in the database.

## Best Practices

1. **Idempotent Operations**: Always use idempotent database operations
2. **Batch Processing**: Process events in batches for better performance
3. **Read Replicas**: Use read replicas for event fetching (configured automatically)
4. **Monitoring**: Monitor view lag and error counts via management API
5. **Error Recovery**: Implement retry logic for transient failures
6. **View Naming**: Use descriptive, unique view names

## Examples

See the test files in `src/test/java` for complete examples:
- `ViewEventHandlerTest` - Basic projector implementation
- `ViewEventFetcherIntegrationTest` - Event fetching with filters
- `ViewManagementServiceIntegrationTest` - Management operations
- `ViewProgressTrackerIntegrationTest` - Progress tracking

## See Also

- [EventStore README](../crablet-eventstore/README.md) - Event sourcing library
- [Event Processor](../crablet-event-processor/README.md) - Generic event processor infrastructure
- [Command README](../crablet-command/README.md) - Command framework

