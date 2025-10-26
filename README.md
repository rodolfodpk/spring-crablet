# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern with microservices architecture, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is a library-first event sourcing solution with Spring Boot integration. It provides:

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB Pattern**: Cursor-based optimistic concurrency control without distributed locks
- **Outbox Pattern**: Reliable event publishing to external systems
- **Spring Integration**: Ready-to-use Spring Boot components

## Modules

- **crablet-eventstore** - Core event sourcing library with DCB support
- **crablet-outbox** - Transactional outbox pattern for event publishing

## Quick Start

### Add Dependencies

```xml
<dependencies>
    <!-- EventStore -->
    <dependency>
        <groupId>com.crablet</groupId>
        <artifactId>crablet-eventstore</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Outbox (optional) -->
    <dependency>
        <groupId>com.crablet</groupId>
        <artifactId>crablet-outbox</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Build and Test

Tests use Testcontainers (no external dependencies required):
```bash
mvn clean install
```

All tests pass (260+ tests with 72% code coverage).

## Features

### Event Store

```java
// Append events
List<AppendEvent> events = List.of(
    AppendEvent.builder("WalletOpened")
        .tag("wallet_id", "wallet-123")
        .data(new WalletOpened("Alice"))
        .build()
);

AppendCondition condition = AppendCondition.builder()
    .tags("wallet_id", "wallet-123")
    .afterCursor(cursor)
    .build();

eventStore.appendIf(events, condition);
```

### Query and Project Events

```java
// Using EventStore for production (with state projection)
Query query = QueryBuilder.create()
    .hasTag("wallet_id", "wallet-123")
    .eventNames("DepositMade", "WithdrawalMade")
    .build();

// Project state from events
ProjectionResult<WalletState> result = eventStore.project(
    query,
    Cursor.zero(),  // or use a cursor from last read
    WalletState.class,
    List.of(walletProjector)
);

WalletState state = result.state();
Cursor newCursor = result.cursor();
```

### Command Handling

```java
@Component
public class DepositCommandHandler implements CommandHandler<DepositCommand> {
    @Override
    public CommandResult handle(DepositCommand command) {
        // Business logic
        DepositMade event = new DepositMade(command.amount());
        return CommandResult.success(AppendEvent.builder("DepositMade")
            .tag("wallet_id", command.walletId())
            .data(event)
            .build());
    }
}
```

## Documentation

### Core Documentation
- **[EventStore README](crablet-eventstore/README.md)** - Event sourcing library guide
- **[Outbox README](crablet-outbox/README.md)** - Outbox pattern library guide
- **[DCB Pattern](crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation

### Advanced Features
- **[Read Replicas](crablet-eventstore/docs/READ_REPLICAS.md)** - PostgreSQL read replica configuration
- **[PgBouncer Guide](crablet-eventstore/docs/PGBOUNCER.md)** - Connection pooling
- **[Outbox Pattern](crablet-outbox/docs/OUTBOX_PATTERN.md)** - Event publishing
- **[Outbox Metrics](crablet-outbox/docs/OUTBOX_METRICS.md)** - Monitoring

## Architecture Highlights

- **DCB Pattern**: Optimistic concurrency control using cursors
- **Java 25**: Records, sealed interfaces, virtual threads
- **Spring Boot 3.5**: Full Spring integration
- **PostgreSQL**: Primary database with optional read replicas
- **Comprehensive Testing**: 260+ tests, 72% code coverage

## License

MIT License - see [LICENSE](LICENSE) file for details.
