# Spring Boot Java DCB Event Sourcing Solution

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java 25 implementation of the DCB (Dynamic Consistency Boundary) event sourcing pattern, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

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

## Complete DCB Workflow

Here's a complete example showing the DCB pattern with conflict detection and idempotency:

```java
import com.crablet.eventstore.store.*;
import com.crablet.eventstore.dcb.*;
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
            
            // 4. Create event with withdrawal_id for idempotency
            AppendEvent event = AppendEvent.builder("WithdrawalMade")
                .tag("wallet_id", walletId)
                .tag("withdrawal_id", withdrawalId)  // Prevents duplicate operations
                .data(new WithdrawalMade(amount))
                .build();
            
            // 5. Build condition with BOTH checks:
            //    - DCB conflict: balance changed since cursor?
            //    - Idempotency: withdrawal_id already processed?
            AppendCondition condition = new AppendConditionBuilder(decisionModel, cursor)
                .withIdempotencyCheck("WithdrawalMade", "withdrawal_id", withdrawalId)
                .build();
            
            // 6. AppendIf validates both conditions
            eventStore.appendIf(List.of(event), condition);
            return CommandResult.success(event);
            
        } catch (ConcurrencyException e) {
            // Balance changed concurrently - retry with fresh state
            return handleWithdrawal(walletId, withdrawalId, amount);
        }
    }
}
```

**What this demonstrates:**
- **Decision Model**: Query defines which events affect business decisions
- **Conflict Detection**: Checks if balance changed since cursor → throws `ConcurrencyException` → retry
- **Idempotency**: `withdrawal_id` prevents duplicate operations even on retry
- **Retry Logic**: Handles concurrent modifications by re-projecting with fresh cursor

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
