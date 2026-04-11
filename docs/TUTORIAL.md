# Crablet Tutorial Series

This repository should not start with one giant tutorial. Crablet is easier to learn as a sequence of small guides, each introducing one concept and one module at a time.

**Recommended order**

1. [Part 1: Event Store Basics](tutorials/01-event-store-basics.md)
2. [Part 2: Commands](tutorials/02-commands.md)
3. [Part 3: DCB Consistency Boundaries](tutorials/03-dcb-consistency-boundaries.md)
4. [Part 4: Views](tutorials/04-views.md)
5. [Part 5: Automations](tutorials/05-automations.md)
6. [Part 6: Outbox](tutorials/06-outbox.md)

**What you will build**

The series uses the same conference talk submission domain throughout:

- speakers submit talks
- organizers accept or reject them
- conference capacity is enforced with DCB consistency checks
- views project current state
- automations react to domain changes
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

In production, the normal recommendation is:

- run **1 application instance** when short downtime during restart is acceptable
- run **2 instances at most** when you want active/failover behavior

This guidance is the same whether you deploy with Docker Compose, Kubernetes, ECS, Nomad, or plain VMs. The poller uses leader election so only one instance is actively processing at a time. Extra replicas do not increase throughput for the same processor set; they mostly add standby capacity and operational complexity.

For deeper details, see [Leader Election](LEADER_ELECTION.md) and the module READMEs for `event-poller`, `views`, `automations`, and `outbox`.
