# Crablet: Java DCB Event Sourcing Light Framework

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Java 25 light framework for DCB (Dynamic Consistency Boundary) event sourcing, leveraging previous experiments from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is a lightweight event sourcing framework with Spring Boot integration. It provides both framework-style command handling and library-style direct EventStore access.

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB**: Cursor-based optimistic concurrency control without distributed locks
- **Outbox**: Reliable event publishing to external systems
- **Spring Integration**: Ready-to-use Spring Boot components

## Framework vs Library

Crablet provides both framework and library capabilities:

- **Framework Mode**: Use `crablet-command` module - implement `CommandHandler<T>` interfaces for automatic discovery and orchestration
- **Library Mode**: Use `crablet-eventstore` module directly - full control over event operations without framework overhead

**Why "Light"?**
- Minimal required components (just `CommandHandler` interface for framework mode)
- Small API surface (3-4 interfaces per module)
- No heavy conventions or configuration
- Most components are optional
- Easy to customize and extend

## Modules

- **crablet-eventstore** - Core event sourcing library with DCB support
- **crablet-command** - Command handling framework with automatic handler discovery
- **crablet-outbox** - Light framework component for transactional outbox event publishing

## Quick Start

See module READMEs for dependency information:
- **[EventStore Setup](crablet-eventstore/README.md#maven-coordinates)** - Add eventstore dependency
- **[Command Setup](crablet-command/README.md#maven-coordinates)** - Add command framework dependency (optional)
- **[Outbox Setup](crablet-outbox/README.md#maven-coordinates)** - Add outbox dependency (optional)

### Build and Test

Tests use Testcontainers (no external dependencies required):
```bash
mvn clean install
```

All tests pass.

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
- **[Command README](crablet-command/README.md)** - Command framework guide
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
- **Comprehensive Testing**: Full test coverage with unit and integration tests

## License

MIT License - see [LICENSE](LICENSE) file for details.
