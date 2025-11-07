# Crablet: Java DCB Event Sourcing Light Framework

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Java 25 light framework for DCB (Dynamic Consistency Boundary) event sourcing, leveraging previous experiments from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is a lightweight event sourcing framework with Spring Boot integration.

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB**: Cursor-based optimistic concurrency control without distributed locks
- **Outbox**: Reliable event publishing to external systems
- **Spring Integration**: Ready-to-use Spring Boot components

## Modules

- **crablet-eventstore** (required) - Core event sourcing library with DCB support
- **crablet-command** (optional) - Use for automatic command handler discovery and orchestration
- **crablet-outbox** (optional) - Use for transactional outbox event publishing to external systems
- **crablet-metrics-micrometer** (optional) - Use for Micrometer metrics collection

## DCB Patterns Quick Reference

Crablet implements DCB (Dynamic Consistency Boundary) for event sourcing concurrency control.

| Pattern | Use Case | AppendCondition |
|---------|----------|----------------|
| **Idempotency Check** | Entity creation (OpenWallet) | `withIdempotencyCheck()` |
| **Cursor-based Check** | State-dependent ops (Withdraw, Transfer) | `AppendConditionBuilder(decisionModel, cursor)` |
| **Empty Condition** | Commutative operations (Deposit) | `AppendCondition.empty()` |

📖 See [Command Patterns Guide](crablet-eventstore/docs/COMMAND_PATTERNS.md) for complete examples and detailed explanations.

## Documentation

### Core Documentation
- **[EventStore README](crablet-eventstore/README.md)** - Event sourcing library guide
- **[Command README](crablet-command/README.md)** - Command framework guide
- **[Outbox README](crablet-outbox/README.md)** - Outbox library guide
- **[Metrics README](crablet-metrics-micrometer/README.md)** - Metrics collector guide
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

## Build and Test

Tests use Testcontainers (no external dependencies required):
```bash
mvn clean install
```

## License

MIT License - see [LICENSE](LICENSE) file for details.
