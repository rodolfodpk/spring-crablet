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

## Complete DCB Workflow Example

This example shows a complete DCB workflow with conflict detection and idempotency:

```java
import com.crablet.eventstore.store.EventStore;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.dcb.AppendConditionBuilder;
import com.crablet.eventstore.dcb.ConcurrencyException;
import com.crablet.eventstore.query.*;
import com.crablet.eventstore.commands.CommandResult;
import java.math.BigDecimal;
import java.util.List;

@Component
public class WithdrawCommandHandler {
    
    private final EventStore eventStore;
    private final WalletBalanceProjector projector;
    
    public CommandResult handleWithdrawal(String walletId, String withdrawalId, BigDecimal amount) {
        // 1. Define decision model: which events affect withdrawal decision?
        Query decisionModel = QueryBuilder.create()
            .hasTag("wallet_id", walletId)
            .eventNames("WalletOpened", "DepositMade", "WithdrawalMade")
            .build();
        
        try {
            // 2. Project current balance with cursor
            ProjectionResult<WalletBalance> result = eventStore.project(
                decisionModel,
                Cursor.zero(),
                WalletBalance.class,
                List.of(projector)
            );
            
            WalletBalance balance = result.state();
            Cursor cursor = result.cursor();
            
            // 3. Business logic: check sufficient funds
            if (balance.amount().compareTo(amount) < 0) {
                return CommandResult.emptyWithReason("Insufficient funds");
            }
            
            // 4. Create event
            AppendEvent event = AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", withdrawalId)  // For idempotency
                .data(new WithdrawalMade(amount))
                .build();
            
            // 5. Build condition with BOTH checks:
            //    - DCB conflict check: balance changed since cursor?
            //    - Idempotency check: withdrawal_id already processed?
            AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WithdrawalMade", "withdrawal_id", withdrawalId)
                .build();
            
            // 6. AppendIf validates both conditions before inserting
            eventStore.appendIf(List.of(event), condition);
            return CommandResult.success(event);
            
        } catch (ConcurrencyException e) {
            // Balance changed (concurrent deposit/withdrawal) - retry with fresh state
            return handleWithdrawal(walletId, withdrawalId, amount);
        }
    }
}
```

### What This Shows

**Decision Model**: The Query defines which events affect the withdrawal decision (balance-affecting events).

**Conflict Detection**: `AppendCondition` checks if any balance-affecting events appeared after the cursor. If yes, throws `ConcurrencyException` → retry with fresh balance.

**Idempotency**: `withIdempotencyCheck()` searches ALL events for `withdrawal_id`. If found, operation is idempotent (returns success without inserting duplicate).

**Retry Logic**: On conflict, the handler recursively retries with a fresh projection to get the updated balance.

## State Projector

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

