# Crablet: Java DCB Event Sourcing Library

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Java 25 library implementing DCB (Dynamic Consistency Boundary) event sourcing, ported from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is an event sourcing library with Spring Boot integration. It provides:

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB**: Cursor-based optimistic concurrency control without distributed locks
- **Outbox**: Reliable event publishing to external systems
- **Spring Integration**: Ready-to-use Spring Boot components

## Modules

- **crablet-eventstore** - Core event sourcing library with DCB support
- **crablet-outbox** - Transactional outbox for event publishing

## Quick Start

See module READMEs for dependency information:
- **[EventStore Setup](crablet-eventstore/README.md#maven-coordinates)** - Add eventstore dependency
- **[Outbox Setup](crablet-outbox/README.md#maven-coordinates)** - Add outbox dependency (optional)

### Build and Test

Tests use Testcontainers (no external dependencies required):
```bash
mvn clean install
```

All tests pass (260+ tests).

## DCB Patterns

Crablet implements DCB (Dynamic Consistency Boundary) for event sourcing concurrency control.

### Pattern Types

| Pattern | Use Case | AppendCondition |
|---------|----------|----------------|
| **Idempotency Check** | Entity creation (OpenWallet) | `withIdempotencyCheck()` |
| **Cursor-based Check** | State-dependent ops (Withdraw, Transfer) | `AppendConditionBuilder(decisionModel, cursor)` |
| **Empty Condition** | Commutative operations (Deposit) | `AppendCondition.empty()` |

### When to Use Each

- **`AppendCondition.empty()`** - For commutative operations where order doesn't matter
- **`withIdempotencyCheck()`** - For entity creation requiring uniqueness
- **`AppendConditionBuilder(decisionModel, cursor)`** - For state-dependent operations that need conflict detection

📖 See [Command Patterns Guide](crablet-eventstore/docs/COMMAND_PATTERNS.md) for complete examples and detailed explanations.

## Documentation

### Core Documentation
- **[EventStore README](crablet-eventstore/README.md)** - Event sourcing library guide
- **[Outbox README](crablet-outbox/README.md)** - Outbox library guide
- **[DCB Explained](crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation

### Advanced Features
- **[Read Replicas](crablet-eventstore/docs/READ_REPLICAS.md)** - PostgreSQL read replica configuration
- **[PgBouncer Guide](crablet-eventstore/docs/PGBOUNCER.md)** - Connection pooling
- **[EventStore Metrics](crablet-eventstore/docs/METRICS.md)** - EventStore metrics and monitoring
- **[Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md)** - Commutative vs non-commutative operations
- **[Outbox Pattern](crablet-outbox/docs/OUTBOX_PATTERN.md)** - Event publishing
- **[Outbox Metrics](crablet-outbox/docs/OUTBOX_METRICS.md)** - Outbox metrics and monitoring

## Architecture Highlights

- **DCB**: Optimistic concurrency control using cursors
- **Java 25**: Records, sealed interfaces, virtual threads
- **Spring Boot 3.5**: Full Spring integration
- **PostgreSQL**: Primary database with optional read replicas
- **Comprehensive Testing**: 260+ tests

## License

MIT License - see [LICENSE](LICENSE) file for details.
