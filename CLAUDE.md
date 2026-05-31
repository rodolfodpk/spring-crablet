# CLAUDE.md

This file is the repo-level routing hub for Claude Code work in spring-crablet.

## Skill Routing

- Application work, generated Crablet apps, feature slices, app command handlers, app views, automations, outbox, codegen sequencing: invoke `/crablet-app-dev`.
- Greenfield onboarding, repo bootstrap, first workshop, first slice, and evolving app lifecycle: invoke `/crablet-greenfield`.
- Codegen provider config, artifact ownership, repair cycle, and recovery: invoke `/crablet-codegen`.
- Framework module changes, public API work, eventstore/commands/poller internals, shared-fetch, auto-configuration, templates, codegen internals, maintainer docs: invoke `/crablet-maintainer`.
- Event Modeling workshop, generator-ready `event-model.yaml`: invoke `/crablet-event-modeling`.
- Deep DCB explanation, choosing or diagnosing DCB for an application command handler, `ConcurrencyException` analysis: invoke `/crablet-dcb`.
- Writing handler unit tests, integration tests, or scenario tests; `crablet-test-commands` consumption; command audit-linkage in tests: invoke `/crablet-test-authoring`.
- Reviewing a diff/file against repo conventions and closed design decisions (ClockProvider, no-FQN, snake_case tags, `transaction_id` linkage): invoke `/crablet-conventions`.
- Docs diagram renderer rules, actor-board vocabulary, sidecar overlays, or multi-lane board authoring: invoke `/crablet-diagram-advisor`.
- Local build, Testcontainers, MCP codegen loop, module test targets, troubleshooting: invoke `/crablet-local-dev`.

Other tools (invoke by name when needed):
- `/crablet-k8s` — Crablet-specific KEDA, LISTEN/NOTIFY + scale-to-zero, K8s manifest mapping
- `/kubernetes-skill` — generic K8s manifests, Helm, RBAC, security hardening, hallucination prevention
- `/balanced-coupling` — evaluate module coupling; classify balanced vs unbalanced
- `/design` — produce modular architecture designs from functional requirements
- `/review` — modularity analysis using Balanced Coupling model
- `/document` — produce modularity review documents in Markdown and HTML
- `/jspecify` — add jspecify nullability support to Java modules

Searchable signposts:

- Datasource rules, shared-fetch, LISTEN/NOTIFY, generated interface policy, and build graph caveats live in `crablet-maintainer`.
- Docs HTML diagram renderer: `docs/event-model-renderer.js`; board notation in `/crablet-event-modeling` (§ Event Modeling Board Semantics); full renderer/arrow rules in `/crablet-diagram-advisor`.
- Greenfield lifecycle pacing lives in `crablet-greenfield`; feature-slice workflow, MCP `output: src/main/java`, generated app verification, and app implementation defaults live in `crablet-app-dev`.

## Build Commands

See `docs/user/BUILD.md` for full details.

```bash
make install            # Full build with unit tests (recommended)
make install-all-tests  # Full build including integration tests
make test               # Run all tests
make clean              # Clean build artifacts
make start              # Run wallet-example-app (port 8080)
make course-start       # Run course-example-app (port 8081)

# Focused module tests (runs same Makefile prerequisites as `make test`, includes `-am` so sibling
# modules are not taken from a stale ~/.m2 SNAPSHOT):
make test-pl PL=<module-dir>
make test-pl PL=<module-dir> MVN_ARGS='-Dtest=ClassName'

# If calling Maven directly after Makefile prerequisites, always use `-am` with `-pl`:
./mvnw test -pl <module-name> -am
./mvnw test -pl <module-name> -am -Dtest=ClassName
```

Always use `make` targets for repository builds. Do not run root Maven build/test commands directly
until `make build-core`, `make build-test-support`, and `make check-test-support-artifact` have passed; otherwise Maven
can resolve a stale locally installed `crablet-test-support` jar and run tests against old Flyway
migrations. Prefer `make test-pl PL=…` for focused runs; if you use `./mvnw test -pl …` directly,
always add **`-am`** after Makefile prerequisites so upstream reactor modules are rebuilt with the
same invocation. Direct plain `./mvnw test -pl …` (without `-am`) can resolve a stale locally installed
sibling SNAPSHOT.

Tests use Testcontainers for PostgreSQL integration tests. `make install` is the normal maintainer build because `crablet-test-support`, `shared-examples-domain`, codegen, and example apps are outside the main Maven reactor.

## Project Overview

Spring-Crablet is a Java 25 event-sourcing framework for Spring Boot applications using DCB-style consistency boundaries. It stores events in PostgreSQL and layers commands, views, automations, outbox, polling, and metrics as optional modules.

Key technologies:

- Java 25
- Spring Boot 4.0.5
- PostgreSQL 17+
- Maven multi-module project

## Module Map

```text
crablet-db-migrations  [reactor]
  Consolidated Flyway SQL migrations for all framework schemas: event store tables and PL/pgSQL
  functions, command audit table, and processing/progress tables for views, automations, and outbox.
  No Java sources; consumed as a classpath dependency by crablet-eventstore at runtime.

crablet-eventstore  [reactor]
  Core event store implementing three DCB-style atomic append methods (idempotent, commutative,
  non-commutative), tag-based event queries, state projection helpers, and optional read-replica
  routing. Foundational module — all other framework modules depend on it directly or transitively.

crablet-commands  [reactor]
  Command handler framework layered on top of crablet-eventstore: Spring @Component-based
  CommandHandler auto-discovery, CommandExecutor, CommandDecision, transaction-scoped command audit
  integration, and DCB-pattern support for idempotent and consistency-guarded commands.

crablet-commands-web  [reactor]
  Optional REST adapter that exposes a generic POST /api/commands endpoint, routing JSON payloads
  to registered CommandHandlers via CommandExecutor. Adds Swagger UI automatically when springdoc
  OpenAPI is on the classpath.

crablet-event-poller  [reactor]
  Shared polling engine used by views, automations, and outbox: per-processor progress tracking,
  PostgreSQL advisory-lock leader election with heartbeat, LISTEN/NOTIFY wakeup to reduce poll lag,
  exponential backoff, and shared-fetch routing for co-located processors.

crablet-views  [reactor]
  Asynchronous materialized view projections built on crablet-event-poller. Provides
  AbstractTypedViewProjector and AbstractViewProjector base classes, idempotent batch processing,
  and independent per-view progress cursors so views catch up at different rates.

crablet-automations  [reactor]
  Event-driven automation handlers (Event Modeling style: read trigger event + state, emit follow-up
  command) built on crablet-event-poller. Supports lifecycle guards, view-backed decision state,
  automatic correlation/causation trace propagation, and independent progress tracking.

crablet-outbox  [reactor]
  Transactional outbox for reliable external publication within the same DB transaction as domain
  events. Supports multiple publishers per topic, per-publisher isolated schedulers, leader election
  with heartbeat, and at-least-once delivery semantics via the shared poller infrastructure.

crablet-observability  [reactor]
  Canonical observation names and tag conventions shared across all modules. No runtime dependency
  on Micrometer or any specific backend — acts as a compile-time naming contract only.

crablet-metrics-micrometer  [reactor]
  Micrometer collector that translates Crablet internal metric events into Prometheus-compatible
  observations for event throughput, concurrency violations, command execution, and poller processing.

crablet-test-support  [installed separately via `make build-test-support`]
  Shared test utilities: InMemoryEventStore for fast unit tests and AbstractCrabletTest backed by
  Testcontainers PostgreSQL. Bundles Flyway migration infrastructure and PostgreSQL driver needed
  for test isolation.

crablet-test-commands  [installed separately via `make build-test-commands`]
  Fast, in-memory BDD base for command handler unit tests (AbstractInMemoryHandlerTest, package
  com.crablet.test.commands). Depends on crablet-commands + crablet-test-support; no Postgres/
  Testcontainers. This is the handler-test base apps consume — not the crablet-commands test-jar.

docs-samples  [reactor, compile-only — no tests]
  Compilable tutorial fixtures that keep documentation aligned with the public API. Covers six
  progressive tutorials (EventStore, Commands, DCB, Views, Automations, Outbox) plus a Getting
  Started wallet example. Built without tests purely to catch API breakage in doc code snippets.

shared-examples-domain  [outside reactor]
  Wallet and Course example domains shared across framework module tests. Includes Jackson
  polymorphism configuration, YAVI command validators, period helpers for closing-the-books pattern,
  and state projectors demonstrating all three DCB concurrency strategies.

examples/wallet-example-app  [outside reactor — `make start`, port 8080]
  Full-stack example showing the complete Crablet runtime: REST API via commands-web, event
  sourcing, view projections, automations, outbox publishers, Micrometer metrics, and a Thymeleaf
  management dashboard. Uses resilience4j for webhook retry in the outbox publisher.

examples/course-example-app  [outside reactor — `make course-start`, port 8081]
  Example demonstrating multi-entity DCB constraints: Course, Student, and Subscription aggregates
  with capacity limits and per-student subscription limits enforced via non-commutative append and
  composite projectors spanning multiple entity streams.

crablet-codegen  [outside reactor — `make codegen-build`]
  AI-first CLI/MCP code generator: reads event-model.yaml and emits command handler interfaces,
  view projectors, automation handlers, outbox publishers, and scenario test stubs. Provider-neutral;
  speaks directly to Anthropic or OpenAI-compatible chat APIs via HTTP without vendor SDK dependencies
  (providers include anthropic, openai, deepseek, ollama, and openai-compatible/custom).

templates/crablet-app
  Starter project template for new Crablet applications: Spring Boot, BOM dependency management,
  commands-web, views, and test-support pre-wired, plus a CLAUDE.md with skill routing for
  AI-assisted development via crablet-codegen MCP and Makefile/CLI workflows.
```

Module dependencies:

- `crablet-db-migrations`: no dependencies on other Crablet modules.
- `crablet-observability`: no dependencies on other Crablet modules.
- `crablet-eventstore`: depends on `crablet-db-migrations` and `crablet-observability`.
- `crablet-commands`: depends on `crablet-eventstore` and `crablet-observability`.
- `crablet-commands-web`: depends on `crablet-commands` plus web APIs.
- `crablet-event-poller`: depends on `crablet-eventstore` and `crablet-observability`.
- `crablet-views`: depends on `crablet-eventstore`, `crablet-event-poller`, and `crablet-observability`.
- `crablet-outbox`: depends on `crablet-eventstore`, `crablet-event-poller`, and `crablet-observability`.
- `crablet-automations`: depends on `crablet-eventstore`, `crablet-event-poller`, `crablet-commands`, `crablet-views`, and `crablet-observability`.
- `crablet-metrics-micrometer`: depends on `crablet-eventstore`, `crablet-commands`, `crablet-outbox`, `crablet-event-poller`, `crablet-views`, `crablet-automations`, and `crablet-observability`.

## Repo Conventions

- Never use fully qualified class names inline in Java code. Add imports.
- Never call `Instant.now()` directly. Inject `ClockProvider` and call `clockProvider.now()`.
- Use `EventType.type(Class)` for event type names.
- Use snake_case tag keys; tag keys are normalized to lowercase and tag values remain case-sensitive.
- Prefer domain-specific query pattern helpers for reused decision models.
- When changing **docs/event-model-renderer.js** or describing a canonical actor board, align with **`/crablet-diagram-advisor`** and **`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`**.
- When changing docs or diagrams, use Event Modeling vocabulary consistently: rows are semantic element layers; lanes are subsystem or bounded-context groupings; time flows left to right.

## Design Decisions

**Command→event linkage via `transaction_id` (intentional).**
`crablet_commands.transaction_id` and `crablet_events.transaction_id` share the same `pg_current_xact_id()` value when both writes happen in the same database transaction. This is the join key between the two tables. Do not propose adding a `command_id` column to `crablet_events` or `crablet_event_tags` as an alternative linkage mechanism — that decision is closed.

The invariant this relies on: `CommandAuditStore.storeCommand` must always be called on the transaction-scoped store (`ConnectionScopedEventStore`) inside `executeInTransaction`, never on the top-level `EventStoreImpl`. `CommandExecutorImpl` upholds this. Any test or caller that wants command audit linkage must use `executeInTransaction` and cast the scoped store to `CommandAuditStore`.

## Documentation Quick Links

- Documentation layout: `docs/README.md`
- User docs: `docs/user/README.md`
- Framework development docs: `docs/dev/README.md`
- Build: `docs/user/BUILD.md`
- Configuration: `docs/user/CONFIGURATION.md`
- Troubleshooting: `docs/user/TROUBLESHOOTING.md`
- Performance: `docs/user/PERFORMANCE.md`
- EventStore: `crablet-eventstore/README.md`
- Commands: `crablet-commands/README.md`
- Event processor: `crablet-event-poller/README.md`
- Views: `crablet-views/README.md`
- Outbox: `crablet-outbox/README.md`
- DCB explained: `crablet-eventstore/docs/DCB_AND_CRABLET.md`
- Command patterns: `crablet-eventstore/docs/COMMAND_PATTERNS.md`
- Leader election: `docs/user/LEADER_ELECTION.md`
- AI-first workflow: `docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`
- Feature slice workflow: `docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`
- Event model format & diagram projection: `docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`
- HTML diagram renderer: `docs/event-model-renderer.js`
- Codegen: `crablet-codegen/README.md`
- Starter template: `templates/crablet-app/README.md`
- Concept map source: `docs/examples/concepts.md`

## Key Package Locations

- EventStore: `crablet-eventstore/src/main/java/com/crablet/eventstore/`
- Command framework: `crablet-commands/src/main/java/com/crablet/command/`
- Views: `crablet-views/src/main/java/com/crablet/views/`
- Outbox: `crablet-outbox/src/main/java/com/crablet/outbox/`
- Test utilities: `crablet-test-support/src/main/java/com/crablet/test/`
- DCB integration helpers: `crablet-test-support/src/main/java/com/crablet/eventstore/integration/`
- Wallet examples: `shared-examples-domain/src/main/java/com/crablet/examples/wallet/`
- Course examples: `shared-examples-domain/src/main/java/com/crablet/examples/course/`
