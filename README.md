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
- Optional views, automations, outbox, and metrics after the command side is working

## 5-Minute Quickstart

For learning, start with **one application instance** running the full stack together. The example app expects a local PostgreSQL database named `wallet_db`; see the [Quickstart](docs/QUICKSTART.md) for the exact first-run setup.

```bash
make install
make start
```

Create a wallet in the example app:

```bash
curl -X POST http://localhost:8080/api/wallets \
  -H 'Content-Type: application/json' \
  -d '{
    "walletId": "wallet-123",
    "owner": "Jane Doe",
    "initialBalance": 100
  }'
```

Then read it back:

```bash
curl http://localhost:8080/api/wallets/wallet-123
```

Next steps:

| Goal | Read |
|------|------|
| Run the wallet app | [Quickstart](docs/QUICKSTART.md) |
| Start a fresh repository | [Create A New Crablet App](docs/CREATE_A_CRABLET_APP.md) |
| Understand the one-instance learning setup | [Learning Mode](docs/LEARNING_MODE.md) |
| Inspect the complete example | [Wallet Example App](wallet-example-app/README.md) |

## When Crablet Fits

Crablet is a good fit when command decisions depend on more than one entity stream, consistency is naturally query-based, and you want the code to make concurrency semantics explicit.

It is probably not the right tool if plain CRUD is enough, one aggregate per command already fits your domain, or your team is not ready to standardize on Java 25.

## Learning And Deployment

- **Learning mode:** run one application instance with commands, views, automations, and outbox together. See [Learning Mode](docs/LEARNING_MODE.md).
- **Command-only production:** applications using only `crablet-eventstore` and `crablet-commands` can scale horizontally in the normal Spring Boot way. See [Commands-First Adoption](docs/COMMANDS_FIRST_ADOPTION.md).
- **Poller-backed production:** applications enabling views, outbox, or automations should default to **one application instance per cluster**. See [Deployment Topology](docs/DEPLOYMENT_TOPOLOGY.md).

## Modules

| Area | Modules |
|------|---------|
| Core runtime | [Event Store](crablet-eventstore/README.md), [Commands](crablet-commands/README.md) |
| Optional add-ons | [Views](crablet-views/README.md), [Outbox](crablet-outbox/README.md), [Automations](crablet-automations/README.md), [Command Web API](crablet-commands-web/README.md), [Micrometer metrics](crablet-metrics-micrometer/README.md) |
| Support and examples | [Test support](crablet-test-support/README.md), [Wallet example app](wallet-example-app/README.md), shared example domain code, compiled docs samples |
| Internal infrastructure | [Event Poller](crablet-event-poller/README.md) powers the poller-backed modules |

## Why Java 25

Crablet intentionally targets Java 25. The framework leans on modern Java features such as records, sealed types, and pattern matching to keep the public API small and explicit.

## DCB At A Glance

Traditional event sourcing ties consistency to a single aggregate stream. DCB lets you define consistency boundaries dynamically, so a command can check consistency across multiple entity types using a query.

Crablet maps that model onto three append methods. These are **Crablet API terms**, not DCB spec vocabulary:

| Method | Use case | Concurrency check |
|--------|----------|-------------------|
| `appendIdempotent` | Entity creation and duplicate prevention | Uniqueness/idempotency check |
| `appendNonCommutative` | State-dependent operations | Stream-position-based conflict detection |
| `appendCommutative` | Order-independent operations | None, optionally guarded by lifecycle checks |

Read more in [DCB And Crablet](crablet-eventstore/docs/DCB_AND_CRABLET.md) and [Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md).

## Where To Go Next

| Topic | Links |
|-------|-------|
| Start | [Quickstart](docs/QUICKSTART.md), [Create A New App](docs/CREATE_A_CRABLET_APP.md), [Tutorial](docs/TUTORIAL.md), [Learning Mode](docs/LEARNING_MODE.md) |
| Architecture | [Deployment Topology](docs/DEPLOYMENT_TOPOLOGY.md), [DCB And Crablet](crablet-eventstore/docs/DCB_AND_CRABLET.md), [Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md) |
| Operations | [Management API](docs/MANAGEMENT_API.md), [Leader Election](docs/LEADER_ELECTION.md), [Build](docs/BUILD.md) |
| Database and proxies | [PgBouncer](crablet-eventstore/docs/PGBOUNCER.md), [PgCat](crablet-eventstore/docs/PGCAT.md), [Open J Proxy](crablet-eventstore/docs/OJP.md) |

## License

MIT License - see [LICENSE](LICENSE).
