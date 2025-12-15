# Crablet: Java DCB Event Sourcing Light Framework

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Java 25 light framework for DCB (Dynamic Consistency Boundary) event sourcing, leveraging previous experiments from [crablet](https://github.com/rodolfodpk/crablet) (Kotlin) and [go-crablet](https://github.com/rodolfodpk/go-crablet) (Go).

## Overview

Crablet is a lightweight event sourcing framework with Spring Boot integration.

- **Event Sourcing**: Complete audit trail with state reconstruction
- **DCB**: Defines consistency boundaries dynamically based on criteria (queries) rather than fixed aggregates. Uses cursors (event positions) to detect concurrent modifications and prevent conflicts - no distributed locks needed
- **Outbox**: Reliable event publishing to external systems
- **Views**: Asynchronous view projections for materialized read models
- **Spring Integration**: Ready-to-use Spring Boot components

## Modules

- **crablet-eventstore** (required) - Core event sourcing library with DCB support
- **crablet-command** (optional) - Use for automatic command handler discovery and orchestration
- **crablet-outbox** (optional) - Use for transactional outbox event publishing to external systems
- **crablet-views** (optional) - Use for asynchronous view projections and materialized read models
- **crablet-metrics-micrometer** (optional) - Use for Micrometer metrics collection

## DCB Patterns Quick Reference

DCB (Dynamic Consistency Boundary) redefines consistency granularity in event-sourced systems, moving from fixed aggregates (event streams) to dynamically defined consistency boundaries based on criteria (queries). It uses cursors (event positions) to detect concurrent modifications and prevent conflicts - all without distributed locks.

| Pattern | Use Case | AppendCondition |
|---------|----------|----------------|
| **Idempotency Check** | Entity creation (OpenWallet) | `withIdempotencyCheck()` |
| **Cursor-based Check** | State-dependent ops (Withdraw, Transfer) | `AppendConditionBuilder(decisionModel, cursor)` |
| **Empty Condition** | Commutative operations (Deposit) | `AppendCondition.empty()` (mainly for tests) |

📖 See [Command Patterns Guide](crablet-eventstore/docs/COMMAND_PATTERNS.md) for complete examples and detailed explanations.

**DCB was introduced by [Sara Pellegrini](https://dcb.events/) in her blog post "Killing the Aggregate"**. See the [official DCB specification](https://dcb.events/) and [presentation by Sara Pellegrini & Milan Savic](https://www.youtube.com/watch?v=0iP65Durhbs) for more information.

## Documentation

### Core Documentation
- **[EventStore README](crablet-eventstore/README.md)** - Event sourcing library guide
- **[Command README](crablet-command/README.md)** - Command framework guide
- **[Outbox README](crablet-outbox/README.md)** - Outbox library guide
- **[Views README](crablet-views/README.md)** - View projections and materialized read models guide
- **[Metrics README](crablet-metrics-micrometer/README.md)** - Metrics collector guide
- **[DCB Explained](crablet-eventstore/docs/DCB_AND_CRABLET.md)** - Detailed DCB explanation
- **[Testing Guide](crablet-eventstore/TESTING.md)** - Testing strategy, unit tests, and integration tests

### Advanced Features
- **[Closing the Books Pattern](crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md)** - Period segmentation using `@PeriodConfig` annotation for performance optimization
- **[Read Replicas & DataSource Configuration](crablet-eventstore/docs/READ_REPLICAS.md)** - Primary and read replica datasource setup with performance benefits
- **[PgBouncer Guide](crablet-eventstore/docs/PGBOUNCER.md)** - Connection pooling
- **[EventStore Metrics](crablet-eventstore/docs/METRICS.md)** - EventStore metrics and monitoring
- **[Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md)** - Commutative vs non-commutative operations
- **[Outbox Pattern](crablet-outbox/docs/OUTBOX_PATTERN.md)** - Event publishing
- **[Outbox Metrics](crablet-outbox/docs/OUTBOX_METRICS.md)** - Outbox metrics and monitoring

## Architecture Highlights

- **DCB**: Optimistic concurrency control using event position tracking (cursors)
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
