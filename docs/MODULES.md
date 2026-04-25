# Crablet modules

Crablet is split into a small required write-side core and optional libraries you add by capability. This page lists module areas and when to adopt each. For **poller-backed module deployment and scaling rules** (views, outbox, automations), see [Deployment Topology](DEPLOYMENT_TOPOLOGY.md) — the constraints are documented there, not duplicated below.

| Area | Modules |
|------|---------|
| Core runtime | [Event Store](../crablet-eventstore/README.md), [Commands](../crablet-commands/README.md) |
| Optional add-ons | [Views](../crablet-views/README.md), [Outbox](../crablet-outbox/README.md), [Automations](../crablet-automations/README.md), [Command Web API](../crablet-commands-web/README.md), [Micrometer metrics](../crablet-metrics-micrometer/README.md), [Observability](OBSERVABILITY.md) |
| Support and examples | [Test support](../crablet-test-support/README.md), [Wallet example app](../wallet-example-app/README.md), shared example domain code, compiled docs samples |
| Internal infrastructure | [Event Poller](../crablet-event-poller/README.md) powers the poller-backed modules |
| AI-first tooling | [Embabel Codegen](../embabel-codegen/README.md) — generates code from event-model.yaml; [Templates](../templates/README.md) — starter project |

## Module boundaries

Crablet has a small required write-side core and several optional features that can be added
independently. Application teams should choose modules by capability, not by adopting the whole
stack at once.

| Boundary | Module | Responsibility | When to add it |
|----------|--------|----------------|----------------|
| Event store | `crablet-eventstore` | Append events, run DCB consistency checks, query streams by type/tag/position, provide `ClockProvider`, `ReadDataSource`, and `WriteDataSource` infrastructure | Always. This is the persistence and consistency core. |
| Write model | `crablet-commands` | Command handler contracts, `CommandDecision` types, `CommandExecutor`, command audit records, and atomic command-to-event execution | Add when the application accepts commands. This is the normal production entry point. |
| HTTP command adapter | `crablet-commands-web` | Generic Spring MVC endpoint for dispatching commands through `CommandExecutor` | Add when you want a framework-provided HTTP command API instead of hand-written controllers. |
| Poller infrastructure | `crablet-event-poller` | Shared scheduling, leader election, progress tracking, backoff, wakeup handling, pause/resume/reset support | Usually pulled transitively by views, automations, or outbox. Depend on it directly only for custom poller-backed modules. |
| Read models | `crablet-views` | Asynchronous view projection from stored events into query-optimized tables, with per-view subscriptions and management operations | Add after the write side works and users need fast reads, dashboards, or query APIs. |
| Internal follow-up behavior | `crablet-automations` | Event-triggered policies that return `AutomationDecision`s, usually executing commands through `CommandExecutor` | Add when one stored event should cause application behavior inside the same bounded context. |
| External publication | `crablet-outbox` | Reliable event publication to external systems through `OutboxPublisher` implementations, with per-topic/per-publisher progress | Add when events must leave the application boundary: HTTP webhooks, Kafka, analytics, CRM, email, or integrations. |
| Metrics | `crablet-metrics-micrometer` | Micrometer listeners for command, view, automation, outbox, and processor metrics | Add when operating Crablet in environments with Prometheus, Grafana, or other Micrometer backends. |

The **write model** is synchronous: a command handler decides, `CommandExecutor` checks consistency,
and the event store appends within the command transaction. This path can run by itself with only
`crablet-eventstore` and `crablet-commands`.

The **read model and side-effect features** are asynchronous: views, automations, and outbox all read
committed events through `crablet-event-poller`, maintain their own progress, and can be paused,
resumed, reset, or isolated as worker services. These features should be added deliberately after
the command side is stable.

Use **views** for query state, **automations** for internal follow-up decisions, and **outbox** for
external delivery. Automations should not call external systems directly; publish externally through
outbox so retries, progress, and deduplication are explicit.

Those modules do not contain or depend at runtime on the example wallet/course application code. Example-only code lives in `shared-examples-domain`, `wallet-example-app`, and `docs-samples`. Some Crablet modules use `shared-examples-domain` as a test-scoped dependency only; it is not part of the runtime dependency graph for users.
