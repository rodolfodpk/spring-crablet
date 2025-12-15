# Crablet Views

[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg?component=module_views)](https://codecov.io/gh/rodolfodpk/spring-crablet)

Light framework component for asynchronous view projections using materialized read models with Spring Boot integration.

## Overview

Crablet Views provides a complete solution for building materialized read models from event streams:

- **Asynchronous Projections**: Events are processed asynchronously using the generic event processor infrastructure
- **Tag-Based Subscriptions**: Subscribe to events by type and/or tags
- **Independent Progress Tracking**: Each view tracks its own processing progress independently
- **JOOQ Integration**: Project events into relational tables using JOOQ
- **Idempotent Operations**: Built-in support for at-least-once processing semantics
- **Spring Integration**: Ready-to-use Spring Boot components and auto-configuration
- **Management API**: REST API for monitoring and controlling view projections

## Features

- **ViewProjector Interface**: Simple interface for projecting events into materialized views
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
- Spring Boot JDBC
- JOOQ (for database operations)
- PostgreSQL JDBC Driver

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

```java
@Component
public class WalletViewProjector implements ViewProjector {
    
    private final DSLContext dsl;
    
    public WalletViewProjector(DSLContext dsl) {
        this.dsl = dsl;
    }
    
    @Override
    public String getViewName() {
        return "wallet-view";
    }
    
    @Override
    public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) {
        int handled = 0;
        
        for (StoredEvent event : events) {
            if ("WalletOpened".equals(event.type())) {
                WalletOpened opened = deserialize(event.data(), WalletOpened.class);
                dsl.insertInto(WALLET_VIEW)
                    .set(WALLET_VIEW.WALLET_ID, opened.walletId())
                    .set(WALLET_VIEW.BALANCE, BigDecimal.ZERO)
                    .onConflict(WALLET_VIEW.WALLET_ID)
                    .doUpdate()
                    .set(WALLET_VIEW.BALANCE, WALLET_VIEW.BALANCE)
                    .execute();
                handled++;
            } else if ("DepositMade".equals(event.type())) {
                DepositMade deposit = deserialize(event.data(), DepositMade.class);
                dsl.update(WALLET_VIEW)
                    .set(WALLET_VIEW.BALANCE, WALLET_VIEW.BALANCE.add(deposit.amount()))
                    .where(WALLET_VIEW.WALLET_ID.eq(deposit.walletId()))
                    .execute();
                handled++;
            }
            // ... handle other event types
        }
        
        return handled;
    }
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
- **Leader Election**: Uses PostgreSQL advisory locks for distributed leader election
- **Backoff Strategy**: Exponential backoff for error recovery

**Recommended deployment:** Run exactly 2 instances (1 leader + 1 backup) for high availability.

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

## Idempotency

View projectors **MUST** use idempotent operations since events may be processed multiple times due to at-least-once semantics.

**Recommended patterns:**
- Use JOOQ `store()` method (handles INSERT/UPDATE automatically)
- Use SQL `ON CONFLICT` clauses
- Use upsert operations

**Example:**
```java
dsl.insertInto(WALLET_VIEW)
    .set(WALLET_VIEW.WALLET_ID, walletId)
    .set(WALLET_VIEW.BALANCE, balance)
    .onConflict(WALLET_VIEW.WALLET_ID)
    .doUpdate()
    .set(WALLET_VIEW.BALANCE, WALLET_VIEW.BALANCE)
    .execute();
```

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
ViewSubscriptionConfig.builder("order-view")
    .eventTypes("OrderCreated", "OrderCancelled")
    .requiredTags("order-id")           // Must have order-id tag
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

