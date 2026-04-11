# Crablet: Java DCB Event Sourcing Light Framework

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight Java 25 event sourcing framework with Spring Boot integration, built around the **DCB (Dynamic Consistency Boundary)** pattern — an alternative to traditional aggregates that enforces consistency through queries rather than fixed event streams.

## Getting Started

```bash
make install
```

No external PostgreSQL needed — integration tests spin up a container via Testcontainers.

- New to Crablet? → **[Tutorial](docs/TUTORIAL.md)** walks through a full example step by step
- Build details → **[Build Guide](docs/BUILD.md)**

## Modules

| Module | Scope | Description |
|--------|-------|-------------|
| **crablet-eventstore** | required | Core event sourcing library with DCB support |
| **crablet-commands** | optional | Automatic command handler discovery and orchestration |
| **crablet-event-poller** | optional | Generic polling infrastructure: leader election and backoff |
| **crablet-outbox** | optional | Transactional outbox for reliable external event publishing |
| **crablet-views** | optional | Asynchronous materialized read models |
| **crablet-automations** | optional | Event-driven policies and sagas |
| **crablet-metrics-micrometer** | optional | Micrometer metrics collection |
| **crablet-test-support** | test | InMemoryEventStore, AbstractCrabletTest, DCBTestHelpers, shared DB migrations |

## DCB at a Glance

Traditional event sourcing ties consistency to a single aggregate stream. DCB lets you define consistency boundaries dynamically — a single command can check consistency across multiple entity types using a query, without distributed locks.

**DCB was introduced by [Sara Pellegrini](https://dcb.events/) in her blog post "Killing the Aggregate"** — see the [official spec](https://dcb.events/) and [presentation](https://www.youtube.com/watch?v=0iP65Durhbs).

📖 [DCB Explained](crablet-eventstore/docs/DCB_AND_CRABLET.md) — deep dive into DCB vs aggregates

## Crablet's API at a Glance

Crablet maps DCB's consistency model onto three append methods. These are **Crablet's library API** — not DCB spec vocabulary:

| Method | Use Case | Concurrency check |
|--------|----------|-------------------|
| `appendIdempotent` | Entity creation (e.g. OpenWallet) | Advisory lock uniqueness check |
| `appendNonCommutative` | State-dependent ops (e.g. Withdraw, Transfer) | StreamPosition-based conflict detection |
| `appendCommutative` | Order-independent ops (e.g. Deposit) | None (optional lifecycle guard) |

📖 [Command Patterns Guide](crablet-eventstore/docs/COMMAND_PATTERNS.md) — complete examples and decision tree

## Requirements

- Java 25
- Docker (required for integration tests via Testcontainers)
- Maven (wrapper included — `./mvnw`)

## Documentation

### Start here
- **[Tutorial](docs/TUTORIAL.md)** — Step-by-step introduction to all framework features
- **[Getting Started](crablet-eventstore/GETTING_STARTED.md)** — Integrate Crablet into your own project

### Module guides
- **[EventStore](crablet-eventstore/README.md)** — Core event sourcing API
- **[Commands](crablet-commands/README.md)** — Command handler framework
- **[Event Poller](crablet-event-poller/README.md)** — Generic event processing infrastructure
- **[Views](crablet-views/README.md)** — Asynchronous view projections
- **[Outbox](crablet-outbox/README.md)** — Transactional outbox pattern
- **[Automations](crablet-automations/README.md)** — Event-driven automations (policies/sagas)
- **[Metrics](crablet-metrics-micrometer/README.md)** — Micrometer metrics for all modules
- **[Test Support](crablet-test-support/README.md)** — Shared test infrastructure
- **[Testing Guide](crablet-eventstore/TESTING.md)** — Unit vs integration testing strategy

### Deep dives
- **[Leader Election](docs/LEADER_ELECTION.md)** — Distributed leader election with PostgreSQL advisory locks
- **[Closing the Books](crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md)** — Period segmentation with `@PeriodConfig` for performance
- **[Read Replicas](crablet-eventstore/docs/READ_REPLICAS.md)** — Primary and read replica datasource setup
- **[PgBouncer](crablet-eventstore/docs/PGBOUNCER.md)** — Connection pooling compatibility
- **[Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md)** — Commutative vs non-commutative operations
- **[Outbox Pattern](crablet-outbox/docs/OUTBOX_PATTERN.md)** — How the outbox integrates with DCB
- **[Build Guide](docs/BUILD.md)** — Build order, Makefile commands, cyclic dependency explanation

## License

MIT License — see [LICENSE](LICENSE) file for details.
