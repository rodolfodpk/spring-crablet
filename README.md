# Crablet: AI-Assisted Event-Sourced Spring Applications From Event Models

[![Java CI](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml/badge.svg)](https://github.com/rodolfodpk/spring-crablet/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/rodolfodpk/spring-crablet/branch/main/graph/badge.svg)](https://codecov.io/gh/rodolfodpk/spring-crablet)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Crablet helps Spring teams turn an event-modeled domain into a working event-sourced application. It uses AI-assisted generation to produce the structural code around commands, events, views, automations, outbox publishers, and tests, then runs that code on a small Java 25 Spring Boot runtime.

## Why Crablet May Be Useful

- Event-model-first workflow for DCB-style domains
- AI-assisted generation for commands, events, handlers, views, automations, outbox publishers, and tests
- Cross-entity consistency without forcing everything into one aggregate stream
- Small Java runtime for consistency, persistence, polling, and operational behavior
- Manual APIs available when generated code needs customization

## AI-First Workflow

The only tool you interact with is Claude Code. You describe outcomes in plain language; Claude handles modeling, planning, generating, and repairing — entirely through conversation.

### One-time setup

**Prerequisites:** Java 25, [Claude Code CLI](https://claude.ai/code), `ANTHROPIC_API_KEY`

```bash
# 1. Build the codegen JAR (from this repo)
make install && make codegen-build

# 2. Copy the template and drop the JAR in place
cp -r templates/crablet-app ../my-service
cp embabel-codegen/target/embabel-codegen.jar ../my-service/tools/
```

### Start Claude Code

```bash
cd ../my-service
export ANTHROPIC_API_KEY=sk-ant-...
claude
```

The template's `.claude/settings.json` wires `embabel-codegen` as an MCP server. You never call `java -jar` directly — Claude Code calls the tools on your behalf.

### Describe one outcome

```text
Add the first vertical slice: Submit Loan Application.

Outcome:
- a customer submits a loan application
- Crablet records LoanApplicationSubmitted
- reviewers can query pending applications

Use the Crablet feature-slice workflow.
Ask for missing facts before changing files.
```

Claude will:
1. Ask for the missing business facts (entity identity, idempotency, required fields, read model columns)
2. Run `/event-modeling` to update `event-model.yaml`
3. Call `embabel_plan` and show you the planned artifact list
4. Wait for your approval before calling `embabel_generate`
5. Fix any compile errors and tell you when to run `./mvnw verify`

Repeat for each new slice. Update `event-model.yaml` when something is structural; edit generated code only for behavior the model cannot express.

**Codegen is optional.** The stable runtime APIs (`CommandHandler`, `ViewProjector`, `CommandExecutor`) work independently — you can build a full Crablet application without the generator. The AI-first path is a productivity layer on top, not a requirement.

| Goal | Read |
|------|------|
| Slice-by-slice guide with dialogue examples | [Feature Slice Workflow](docs/FEATURE_SLICE_WORKFLOW.md) |
| Event Modeling notation and example boards | [Event Modeling](docs/EVENT_MODELING.md) |
| Full workflow and tooling details | [AI-First Workflow](docs/AI_FIRST_WORKFLOW.md) |
| event-model.yaml format | [Event Model Format](docs/EVENT_MODEL_FORMAT.md) |
| Starter template | [Templates](templates/README.md) |
| Codegen CLI and MCP server reference | [Embabel Codegen](embabel-codegen/README.md) |
| Run the wallet app | [Quickstart](docs/QUICKSTART.md) |
| Build manually against the runtime APIs | [Create A New Crablet App Manually](docs/CREATE_A_CRABLET_APP.md) |
| Inspect the complete example | [Wallet Example App](wallet-example-app/README.md) |

## When Crablet Fits

Crablet is a good fit when command decisions depend on more than one entity stream, consistency is naturally query-based, and you want the code to make concurrency semantics explicit.

It is probably not the right tool if plain CRUD is enough, one aggregate per command already fits your domain, or your team is not ready to standardize on Java 25.

## Manual Runtime Path

Crablet can still be used directly as a Java framework. On top of the `EventStore`, the typical write side is a command handler and an executor.

```java
public record WalletOpened(String walletId, String owner) implements WalletEvent {}

@Component
public class OpenWalletCommandHandler
        implements IdempotentCommandHandler<OpenWalletCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore,
                                             OpenWalletCommand command) {
        AppendEvent event = AppendEvent.builder(type(WalletOpened.class))
                .tag(WALLET_ID, command.walletId())
                .data(new WalletOpened(command.walletId(), command.owner()))
                .build();
        return CommandDecision.Idempotent.of(
                event, type(WalletOpened.class), WALLET_ID, command.walletId());
    }
}
```

`CommandExecutor` wraps the handler in a transaction, runs the DCB check, and appends the event atomically. Views, outbox, automations, and an optional HTTP command adapter are layered on top independently.

## Learning And Deployment

- **Learning mode:** run one application instance with commands, views, automations, and outbox together. See [Learning Mode](docs/LEARNING_MODE.md).
- **Command-only production:** applications using only `crablet-eventstore` and `crablet-commands` can scale horizontally in the normal Spring Boot way. See [Commands-First Adoption](docs/COMMANDS_FIRST_ADOPTION.md).
- **Poller-backed production:** applications enabling views, outbox, or automations should default to **one application instance per cluster** for the simplest topology, or use one singleton worker service per poller-backed module for isolation. See [Deployment Topology](docs/DEPLOYMENT_TOPOLOGY.md).

## Modules

| Area | Modules |
|------|---------|
| Core runtime | [Event Store](crablet-eventstore/README.md), [Commands](crablet-commands/README.md) |
| Optional add-ons | [Views](crablet-views/README.md), [Outbox](crablet-outbox/README.md), [Automations](crablet-automations/README.md), [Command Web API](crablet-commands-web/README.md), [Micrometer metrics](crablet-metrics-micrometer/README.md), [Observability](docs/OBSERVABILITY.md) |
| Support and examples | [Test support](crablet-test-support/README.md), [Wallet example app](wallet-example-app/README.md), shared example domain code, compiled docs samples |
| Internal infrastructure | [Event Poller](crablet-event-poller/README.md) powers the poller-backed modules |
| AI-first tooling | [Embabel Codegen](embabel-codegen/README.md) — generates code from event-model.yaml; [Templates](templates/README.md) — starter project |

### Module Boundaries

Crablet has a small required write-side core and several optional features that can be added
independently. Application teams should choose modules by capability, not by adopting the whole
stack at once.

| Boundary | Module | Responsibility | When to add it |
|---|---|---|---|
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
| Start | [AI-First Workflow](docs/AI_FIRST_WORKFLOW.md), [Feature Slice Workflow](docs/FEATURE_SLICE_WORKFLOW.md), [Event Modeling](docs/EVENT_MODELING.md), [Event Model Format](docs/EVENT_MODEL_FORMAT.md), [Quickstart](docs/QUICKSTART.md), [Create A New Crablet App Manually](docs/CREATE_A_CRABLET_APP.md), [Tutorial](docs/TUTORIAL.md), [Learning Mode](docs/LEARNING_MODE.md) |
| Architecture | [Deployment Topology](docs/DEPLOYMENT_TOPOLOGY.md), [DCB And Crablet](crablet-eventstore/docs/DCB_AND_CRABLET.md), [Command Patterns](crablet-eventstore/docs/COMMAND_PATTERNS.md) |
| Operations | [Management API](docs/MANAGEMENT_API.md), [Fault Recovery](docs/FAULT_RECOVERY.md), [Leader Election](docs/LEADER_ELECTION.md), [Build](docs/BUILD.md), [Performance](docs/PERFORMANCE.md), [Troubleshooting](docs/TROUBLESHOOTING.md), [Upgrade Guide](docs/UPGRADE.md) |
| Database and proxies | [Connection Poolers](crablet-eventstore/docs/CONNECTION_POOLERS.md) (PgBouncer, PgCat, OJP) |

## License

MIT License - see [LICENSE](LICENSE).
