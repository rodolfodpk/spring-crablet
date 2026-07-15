# Crablet modules

Crablet is split into a small required write-side core and optional libraries you add by capability. This page lists module areas and when to adopt each. For **poller-backed module deployment and scaling rules** (views, outbox, automations), see [Deployment Topology](DEPLOYMENT_TOPOLOGY.md) — the constraints are documented there, not duplicated below.

| Area | Modules |
|------|---------|
| Core runtime | [Event Store](../../crablet-eventstore/README.md), [Commands](../../crablet-commands/README.md) |
| Optional add-ons | [Views](../../crablet-views/README.md), [Outbox](../../crablet-outbox/README.md), [Automations](../../crablet-automations/README.md), [Command Web API](../../crablet-commands-web/README.md), [Observability](OBSERVABILITY.md), [Micrometer compatibility metrics](../../crablet-metrics-micrometer/README.md) |
| Support and examples | [Test support](../../crablet-test-support/README.md), [Wallet example app](../../examples/wallet-example-app/README.md), [Course example app](../../examples/course-example-app/README.md), shared example domain code, compiled docs samples |
| Internal infrastructure | [Event Poller](../../crablet-event-poller/README.md) powers the poller-backed modules |
| Starter template | [Templates](../../templates/README.md) — starter project for hand-written apps |

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
| External publication | `crablet-outbox` | Reliable event publication outside the application boundary through `OutboxPublisher` implementations, with per-topic/per-publisher progress | Add when stored events must leave the application boundary. |
| Observability conventions | `crablet-observability` | Shared observation names and low-cardinality tag conventions used by Crablet modules | Pulled transitively by framework modules; application code normally does not depend on it directly. |
| Metrics compatibility | `crablet-metrics-micrometer` | Transitional Micrometer collector for legacy Crablet metric events | Add only when you need the existing Prometheus/Grafana dashboard metrics. Prefer Spring Boot Observation with OTLP/OpenTelemetry export for new setups. |

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

## Recommended Application Layout

Crablet does not prescribe how you structure your application code, but the following layout
works well across small-to-large applications. In small apps, all of these can live in a single
Maven/Gradle module. In larger systems, split them to control classpath visibility across
deployment workers.

| Layer | Contents | When to split it out |
|-------|----------|----------------------|
| `domain` | Events, commands, tag constants, shared query pattern helpers | When shared by multiple application modules or workers |
| `view-contracts` | `ViewSubscription` beans and read-model row contracts | When the automations worker must infer wake events or query view tables without running the views processor |
| `views` | `ViewProjector` implementations, view table DDL | When running views in a dedicated worker |
| `automations` | `AutomationHandler` / `ViewBackedAutomationHandler` implementations | When running automations in a dedicated worker |
| `outbox` | `OutboxPublisher` implementations, external integration | When running outbox in a dedicated worker |

**Java APIs are the source of truth.** `event-model.yaml` is optional tooling input for the
pré-1.0/experimental codegen track (see `docs/dev/PRODUCT_ROADMAP.md`). Pure Java consumers do
not need `event-model.yaml` at runtime.

### The `view-contracts` Module in Split Deployments

In a distributed deployment, the automations worker needs `ViewSubscription` beans on its
classpath to infer automation wake events — but it should not run the views processor.

Placing `ViewSubscription` beans and read-model row contracts in a shared `view-contracts` module
(or equivalent name) makes this classpath split explicit:

```
views worker classpath:      domain + view-contracts + views
automations worker classpath: domain + view-contracts + automations
```

With `crablet.views.enabled=false` in the automations worker, the framework picks up
`ViewSubscription` beans for wake-event inference without starting any view processing.
See [Deployment Topology](DEPLOYMENT_TOPOLOGY.md) for the full topology rules.

## Resilience boundaries

Crablet keeps framework-level resilience focused on durable progress and explicit retries:

- `crablet-event-poller` owns polling cadence, idle backoff, leader retry, progress tracking, and
  processor `FAILED` state.
- Poller backoff is idle-load control. Handler failures do not advance progress; they are retried by
  redelivery until the processor reaches its configured error limit.
- `crablet-eventstore` owns database append and query operations. Database failures surface to the
  caller instead of being hidden by framework-level retries.
- `crablet-commands` is the synchronous write path. Validation failures, DCB conflicts, and handler
  failures surface directly to the caller. Safe retries are modeled explicitly through idempotent
  commands.

Crablet modules do not ship a built-in circuit breaker, retry, or time-limit library. Applications
that need circuit breakers should add them around their own `ViewProjector`, `AutomationHandler`, or
`OutboxPublisher` beans.

Those modules do not contain or depend at runtime on the example application code. Example-only code lives in `shared-examples-domain`, `examples/wallet-example-app`, `examples/course-example-app`, and `docs-samples`. Some Crablet modules use `shared-examples-domain` as a test-scoped dependency only; it is not part of the runtime dependency graph for users.
