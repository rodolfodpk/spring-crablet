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
EventStore eventStore = // ... get from Spring context

// Append events
eventStore.append("stream-id", events);

// Query events
eventStore.project(projector, query);

// Optimistic concurrency control
eventStore.appendIf("stream-id", events, appendCondition);
```

### Command Handler

Handle commands and produce events:

```java
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    @Override
    public CommandResult handle(DepositCommand command, StateProjector<WalletBalanceState> projector) {
        WalletBalanceState state = projector.project(command.walletId());
        
        if (state.balance() < 0) {
            return CommandResult.emptyWithReason("Insufficient funds");
        }
        
        DepositMade event = new DepositMade(command.amount());
        return CommandResult.success(AppendEvent.builder()
            .data(event)
            .build());
    }
}
```

### State Projector

Project current state from events:

```java
public class WalletBalanceProjector implements StateProjector<WalletBalanceState> {
    @Override
    public WalletBalanceState project(String streamId, List<StoredEvent> events) {
        return events.stream()
            .map(e -> (WalletEvent) e.eventData())
            .reduce(new WalletBalanceState(0), this::apply, (a, b) -> b);
    }
    
    private WalletBalanceState apply(WalletBalanceState state, WalletEvent event) {
        return switch (event) {
            case DepositMade e -> new WalletBalanceState(state.balance() + e.amount());
            case WithdrawalMade e -> new WalletBalanceState(state.balance() - e.amount());
            default -> state;
        };
    }
}
```

## DCB Pattern

The Dynamic Consistency Boundary pattern uses cursors to ensure consistent event ordering:

```java
// Read events with cursor for consistency check
Cursor cursor = new Cursor(streamId, lastProcessedSequenceNumber);
Query query = Query.builder()
    .streamId(streamId)
    .fromCursor(cursor)
    .build();

// Project state
WalletBalanceState state = eventStore.project(projector, query);

// Append with condition to prevent concurrent modifications
AppendCondition condition = AppendCondition.builder()
    .expectSequenceNumber(cursor.sequenceNumber())
    .build();

eventStore.appendIf(streamId, newEvents, condition);
```

## Querying Events

Query events by tag, event name, or stream:

```java
// Query by tag
Query query = Query.builder()
    .hasTag("wallet-id", "wallet-123")
    .build();

// Query by event name
Query query = Query.builder()
    .eventName("DepositMade")
    .build();

// Query by stream
Query query = Query.builder()
    .streamId("wallet-123")
    .build();
```

## Documentation

- [DCB Pattern](docs/DCB_AND_CRABLET.md) - Detailed explanation of the Dynamic Consistency Boundary pattern

## License

MIT

