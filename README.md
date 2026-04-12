# Crablet: Event Sourcing For Spring Applications That Need Flexible Consistency

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet is a Java 25 event sourcing framework for Spring Boot. It is designed for domains where consistency depends on queries across multiple event streams, tags, or lifecycle events, not just one aggregate stream.

## Why Crablet May Be Useful

- Cross-entity consistency without forcing everything into one aggregate stream
- Small public API centered on `EventStore`, `CommandHandler`, and `CommandExecutor`
- Explicit concurrency semantics: commutative, non-commutative, and idempotent flows
- Optional views, automations, outbox, and metrics when the command side is working

## 5-Minute Quickstart

For learning, start with **one application instance** running the full stack together. That gives you the clearest write-to-read and write-to-side-effect flow.

The example app expects a local PostgreSQL database named `wallet_db`. See [docs/QUICKSTART.md](docs/QUICKSTART.md) for the exact first-run setup.

```bash
make install
make start
```

Then try the wallet example:

```bash
curl -X POST http://localhost:8080/api/wallets \
  -H 'Content-Type: application/json' \
  -d '{
    "walletId": "wallet-123",
    "owner": "Jane Doe",
    "initialBalance": 100
  }'
```

```bash
curl -X POST http://localhost:8080/api/wallets/wallet-123/deposits \
  -H 'Content-Type: application/json' \
  -d '{
    "depositId": "deposit-001",
    "amount": 25,
    "description": "Initial top-up"
  }'
```

```bash
curl http://localhost:8080/api/wallets/wallet-123
```

From there:

- Full quickstart walkthrough: [docs/QUICKSTART.md](docs/QUICKSTART.md)
- Learning-mode guidance: [docs/LEARNING_MODE.md](docs/LEARNING_MODE.md)
- Complete example application: [wallet-example-app/README.md](wallet-example-app/README.md)

## What Problem Crablet Solves

Traditional aggregate-centric event sourcing works well when one command maps cleanly to one stream.

Crablet is aimed at a different shape of problem:

- a command must check state across multiple entities
- consistency depends on query results, not one aggregate instance
- you want event sourcing without adopting a large, prescriptive framework
- you want concurrency rules to be visible in code

## Use Crablet If

- your command decisions depend on more than one entity stream
- you want explicit consistency semantics in the public API
- you prefer Spring Boot integration with a relatively small framework surface
- you want to adopt command handling first and add projections or side effects later
- you are intentionally targeting Java 25

## Don’t Use Crablet If

- plain CRUD is enough
- one aggregate per command already fits your domain well
- you need active-active scaling for poller-backed background processing
- you want a framework that hides most event-sourcing choices behind conventions
- your team is not willing to standardize on Java 25

## Learning And Deployment Modes

### Learning Mode

Run **one application instance** with commands, views, automations, and outbox together. This is the recommended setup for learning Crablet because it makes the full flow visible with the least operational complexity.

See [docs/LEARNING_MODE.md](docs/LEARNING_MODE.md).

### Command-Only Production

If you only use `crablet-eventstore` and `crablet-commands`, your application instances can scale horizontally in the normal Spring Boot way.

See [docs/COMMANDS_FIRST_ADOPTION.md](docs/COMMANDS_FIRST_ADOPTION.md).

### Production With Poller-Backed Modules

If your application enables `crablet-views`, `crablet-outbox`, or `crablet-automations`, default to **one application instance per cluster**. These modules depend on the event poller and should be presented as a simpler, correctness-first deployment model rather than a horizontally scaled active-active path.

See [docs/DEPLOYMENT_TOPOLOGY.md](docs/DEPLOYMENT_TOPOLOGY.md).

## Modules

| Module | Scope | Adoption role | Notes |
|--------|-------|---------------|-------|
| **crablet-eventstore** | required | core | Core event sourcing library with DCB support |
| **crablet-commands** | optional | core | Command handlers and transactional command execution |
| **crablet-event-poller** | optional | infrastructure | Internal processing infrastructure for poller-backed modules |
| **crablet-views** | optional | add-on | Poller-backed read models; default to 1 instance per cluster |
| **crablet-outbox** | optional | add-on | Poller-backed external publishing; default to 1 instance per cluster |
| **crablet-automations** | optional | add-on | Poller-backed policies and sagas; default to 1 instance per cluster |
| **crablet-metrics-micrometer** | optional | add-on | Micrometer metrics collection |
| **crablet-test-support** | test | support | In-memory and integration-test support |

## Why Java 25

Crablet intentionally targets Java 25. The framework leans on modern Java features such as records, sealed types, and pattern matching to keep the public API small and explicit.

This is a deliberate product choice, not an accidental requirement.

## DCB At A Glance

Traditional event sourcing ties consistency to a single aggregate stream. DCB lets you define consistency boundaries dynamically, so a single command can check consistency across multiple entity types using a query.

Crablet maps that model onto three append methods. These are **Crablet API terms**, not DCB spec vocabulary:

| Method | Use case | Concurrency check |
|--------|----------|-------------------|
| `appendIdempotent` | Entity creation and duplicate prevention | Uniqueness/idempotency check |
| `appendNonCommutative` | State-dependent operations | Stream-position-based conflict detection |
| `appendCommutative` | Order-independent operations | None, optionally guarded by lifecycle checks |

- DCB explanation: [crablet-eventstore/docs/DCB_AND_CRABLET.md](crablet-eventstore/docs/DCB_AND_CRABLET.md)
- Command patterns: [crablet-eventstore/docs/COMMAND_PATTERNS.md](crablet-eventstore/docs/COMMAND_PATTERNS.md)

## Documentation

### Start Here

- [docs/QUICKSTART.md](docs/QUICKSTART.md) — 5-minute wallet walkthrough
- [docs/LEARNING_MODE.md](docs/LEARNING_MODE.md) — Recommended one-instance learning setup
- [docs/COMMANDS_FIRST_ADOPTION.md](docs/COMMANDS_FIRST_ADOPTION.md) — Adopt the command side first
- [docs/DEPLOYMENT_TOPOLOGY.md](docs/DEPLOYMENT_TOPOLOGY.md) — Production topology guidance
- [docs/COMPARISONS.md](docs/COMPARISONS.md) — Crablet vs common alternatives

### Tutorials

- [docs/TUTORIAL.md](docs/TUTORIAL.md) — Structured onboarding path
- [docs/tutorials/01-event-store-basics.md](docs/tutorials/01-event-store-basics.md)
- [docs/tutorials/02-commands.md](docs/tutorials/02-commands.md)
- [docs/tutorials/03-dcb-consistency-boundaries.md](docs/tutorials/03-dcb-consistency-boundaries.md)
- [docs/tutorials/04-views.md](docs/tutorials/04-views.md)
- [docs/tutorials/05-automations.md](docs/tutorials/05-automations.md)
- [docs/tutorials/06-outbox.md](docs/tutorials/06-outbox.md)

### Module Guides

- [crablet-eventstore/README.md](crablet-eventstore/README.md) — Core event sourcing API
- [crablet-commands/README.md](crablet-commands/README.md) — Command handler framework
- [crablet-event-poller/README.md](crablet-event-poller/README.md) — Generic event processing infrastructure
- [crablet-views/README.md](crablet-views/README.md) — Asynchronous view projections
- [crablet-outbox/README.md](crablet-outbox/README.md) — Transactional outbox pattern
- [crablet-automations/README.md](crablet-automations/README.md) — Event-driven automations
- [crablet-metrics-micrometer/README.md](crablet-metrics-micrometer/README.md) — Micrometer metrics
- [crablet-test-support/README.md](crablet-test-support/README.md) — Test infrastructure

### Deep Dives

- [docs/LEADER_ELECTION.md](docs/LEADER_ELECTION.md)
- [crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md](crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md)
- [crablet-eventstore/docs/READ_REPLICAS.md](crablet-eventstore/docs/READ_REPLICAS.md)
- [crablet-eventstore/docs/PGBOUNCER.md](crablet-eventstore/docs/PGBOUNCER.md)
- [crablet-eventstore/docs/PGCAT.md](crablet-eventstore/docs/PGCAT.md)
- [crablet-outbox/docs/OUTBOX_PATTERN.md](crablet-outbox/docs/OUTBOX_PATTERN.md)
- [docs/BUILD.md](docs/BUILD.md)

## License

MIT License — see [LICENSE](LICENSE).
