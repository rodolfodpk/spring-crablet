# Crablet EventStore

Event sourcing library with Dynamic Consistency Boundary (DCB) pattern support and Spring Boot integration.

## Overview

Crablet EventStore provides the foundational interfaces and Spring implementations for event sourcing in Java applications. It implements the DCB pattern for optimistic concurrency control without requiring distributed locks.

## Features

- **Event Store Interface**: Simple, idiomatic event sourcing API
- **DCB Pattern**: Optimistic concurrency control using cursors
- **Type-Safe Commands**: Command handler pattern with automatic projection
- **Flexible Querying**: Tag-based event querying and filtering
- **State Projection**: Built-in support for projecting current state from events
- **Spring Integration**: Ready-to-use Spring Boot components and configuration
- **Read Replicas**: Optional PostgreSQL read replica support for horizontal scaling

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- Spring Boot Web, JDBC
- PostgreSQL JDBC Driver
- Jackson (for JSON serialization)
- Resilience4j (for circuit breakers and retries)
- SLF4J (for logging)

## Core Abstractions

### EventStore

The main interface for interacting with the event store:

```java
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.query.ProjectionResult;

EventStore eventStore = // ... get from Spring context

// Append events (DCB uses tags, not stream IDs)
List<AppendEvent> events = List.of(
    AppendEvent.builder("WalletOpened")
        .tag("wallet_id", "wallet-123")
        .data(new WalletOpened("Alice", new BigDecimal("1000")))
        .build()
);
eventStore.append(events);

// Project state from events
Query query = QueryBuilder.create()
    .hasTag("wallet_id", "wallet-123")
    .build();

ProjectionResult<WalletBalance> result = eventStore.project(
    query,
    Cursor.zero(),
    WalletBalance.class,
    List.of(balanceProjector)
);

// Optimistic concurrency control with DCB
AppendCondition condition = AppendCondition.of(result.cursor(), query);
eventStore.appendIf(events, condition);
```

### Command Handler

Handle commands and produce events:

```java
import com.crablet.eventstore.commands.CommandHandler;
import com.crablet.eventstore.commands.CommandResult;
import com.crablet.eventstore.store.AppendEvent;

@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    @Override
    public CommandResult handle(DepositCommand command) {
        // Business logic
        DepositMade event = new DepositMade(command.amount());
        
        return CommandResult.success(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", command.walletId())
                .tag("deposit_id", command.depositId())
                .data(event)
                .build()
        );
    }
}
```

### State Projector

Project current state from events:

```java
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.store.StoredEvent;
import java.util.List;

public class WalletBalanceProjector implements StateProjector<WalletBalance> {
    @Override
    public WalletBalance project(List<StoredEvent> events) {
        return events.stream()
            .map(e -> (WalletEvent) e.data())
            .reduce(
                new WalletBalance(BigDecimal.ZERO), 
                this::apply, 
                (a, b) -> b
            );
    }
    
    private WalletBalance apply(WalletBalance balance, WalletEvent event) {
        return switch (event) {
            case WalletOpened e -> new WalletBalance(e.initialBalance());
            case DepositMade e -> new WalletBalance(balance.amount().add(e.amount()));
            case WithdrawalMade e -> new WalletBalance(balance.amount().subtract(e.amount()));
        };
    }
}
```

## DCB Pattern

The Dynamic Consistency Boundary pattern uses cursors and tags for optimistic concurrency control:

```java
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;

// 1. Build query using tags (not stream IDs)
Query query = QueryBuilder.create()
    .hasTag("wallet_id", walletId)
    .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
    .build();

// 2. Project state with cursor
ProjectionResult<WalletBalance> result = eventStore.project(
    query,
    Cursor.zero(),  // or cursor from previous read
    WalletBalance.class,
    List.of(projector)
);

// 3. Append with condition to prevent concurrent modifications
AppendCondition condition = new AppendConditionBuilder(query, result.cursor())
    .build();

eventStore.appendIf(newEvents, condition);
```

## Querying Events

Query events by tag and event name:

```java
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;

// Query by tag
Query query = QueryBuilder.create()
    .hasTag("wallet_id", "wallet-123")
    .build();

// Query by event names
Query query = QueryBuilder.create()
    .eventNames("DepositMade", "WithdrawalMade")
    .build();

// Query by tag and event names
Query query = QueryBuilder.create()
    .hasTag("wallet_id", "wallet-123")
    .eventNames("DepositMade")
    .build();
```

## Documentation

- [DCB Pattern](docs/DCB_AND_CRABLET.md) - Detailed explanation of the Dynamic Consistency Boundary pattern

## License

MIT

