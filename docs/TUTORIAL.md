# Crablet Tutorial Series

This repository should not start with one giant tutorial. Crablet is easier to learn as a sequence of small guides, each introducing one concept and one module at a time.

Recommended learning setup:

- run **one application instance**
- learn the write path first
- add poller-backed modules in the same app only after the command flow makes sense

**Recommended order**

1. [Part 1: Event Store Basics](tutorials/01-event-store-basics.md)
2. [Part 2: Commands](tutorials/02-commands.md)
3. [Part 3: DCB Consistency Boundaries](tutorials/03-dcb-consistency-boundaries.md)
4. [Part 4: Views](tutorials/04-views.md)
5. [Part 5: Automations](tutorials/05-automations.md)
6. [Part 6: Outbox](tutorials/06-outbox.md)

**What you will build**

The series now stays close to the wallet example used elsewhere in the repository:

- wallets are opened through commands
- deposits and withdrawals update state through events
- insufficient-funds checks use DCB consistency boundaries
- views project current balance and history
- automations react to wallet events
- outbox publishes integration events

**Prerequisites**

- Java 25
- Maven
- Docker for integration tests via Testcontainers

**Reference implementation**

- [wallet-example-app](../wallet-example-app/README.md) for a complete Spring Boot application
- [crablet-eventstore/GETTING_STARTED.md](../crablet-eventstore/GETTING_STARTED.md) for integration setup

## Module Map

| Tutorial | Main module | What you learn |
|---|---|---|
| Part 1 | `crablet-eventstore` | Append and project events |
| Part 2 | `crablet-commands` | Command handlers and transactional command execution |
| Part 3 | `crablet-eventstore` | DCB decision models and optimistic concurrency |
| Part 4 | `crablet-views` | Asynchronous materialized read models |
| Part 5 | `crablet-automations` | Event-driven policies and side effects |
| Part 6 | `crablet-outbox` | Transactional publishing to external systems |

## Deployment Note For Poller-Based Modules

`crablet-views`, `crablet-automations`, and `crablet-outbox` all depend on `crablet-event-poller`.

In production, the default recommendation is:

- run **1 application instance per cluster**

The poller uses leader election so only one instance is actively processing at a time. Extra replicas do not increase throughput for the same processor set; they mainly add standby behavior and operational complexity.

## Configuration Note For Poller-Based Modules

Poller-backed modules use two configuration levels:

- a **global module config** with shared defaults
- a **per-poller-instance config** for one processor

Examples:

- views: global `crablet.views.*` plus one `ViewSubscription` per view
- automations: global `crablet.automations.*` plus one `AutomationHandler` per automation
- outbox: global `crablet.outbox.*` plus one resolved processor per `(topic, publisher)` pair

This is intentional. The event poller always runs per processor instance, even when many processors share the same module-level defaults.

For deeper details, see [Leader Election](LEADER_ELECTION.md) and the module READMEs for `event-poller`, `views`, `automations`, and `outbox`.
