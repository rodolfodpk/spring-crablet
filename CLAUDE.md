# CLAUDE.md

This file is the repo-level routing hub for Claude Code work in spring-crablet.

## Skill Routing

- Application work, generated Crablet apps, feature slices, app command handlers, app views, automations, outbox, codegen sequencing: invoke `/crablet-app-dev`.
- Framework module changes, public API work, eventstore/commands/poller internals, shared-fetch, auto-configuration, templates, codegen internals, maintainer docs: invoke `/crablet-maintainer`.
- Event Modeling workshop, generator-ready `event-model.yaml`: invoke `/event-modeling`.
- Deep DCB explanation, choosing or diagnosing DCB for an application command handler, `ConcurrencyException` analysis: invoke `/dcb`.
- Docs diagram renderer rules, actor-board vocabulary, sidecar overlays, or multi-lane board authoring: invoke `/crablet-diagram-advisor`.

Other tools (invoke by name when needed):
- `/balanced-coupling` — evaluate module coupling; classify balanced vs unbalanced
- `/design` — produce modular architecture designs from functional requirements
- `/review` — modularity analysis using Balanced Coupling model
- `/document` — produce modularity review documents in Markdown and HTML
- `/jspecify-skill` — add jspecify nullability support to Java modules

Searchable signposts:

- Datasource rules, shared-fetch, LISTEN/NOTIFY, generated interface policy, and build graph caveats live in `crablet-maintainer`.
- Docs HTML diagram renderer: `docs/event-model-renderer.js`; board notation in `/event-modeling` (§ Event Modeling Board Semantics); full renderer/arrow rules in `/crablet-diagram-advisor`.
- Feature-slice workflow, MCP `output: src/main/java`, generated app verification, and app implementation defaults live in `crablet-app-dev`.

## Build Commands

See `docs/user/BUILD.md` for full details.

```bash
make install            # Full build with unit tests (recommended)
make install-all-tests  # Full build including integration tests
make test               # Run all tests
make clean              # Clean build artifacts
make start              # Run wallet-example-app (port 8080)
make course-start       # Run course-example-app (port 8081)

# Run specific module tests after dependencies are built
./mvnw test -pl <module-name>
./mvnw test -pl <module-name> -Dtest=ClassName
```

Tests use Testcontainers for PostgreSQL integration tests. `make install` is the normal maintainer build because the examples and shared example domain are outside the Maven reactor.

## Project Overview

Spring-Crablet is a Java 25 event-sourcing framework for Spring Boot applications using DCB-style consistency boundaries. It stores events in PostgreSQL and layers commands, views, automations, outbox, polling, and metrics as optional modules.

Key technologies:

- Java 25
- Spring Boot 4.0
- PostgreSQL 17+
- Maven multi-module project

## Module Map

```text
crablet-eventstore
  Core event store, DCB append methods, queries, projections, migrations.

crablet-commands
  CommandHandler, CommandExecutor, CommandDecision, command audit integration.

crablet-commands-web
  Optional generic HTTP command API backed by CommandExecutor.

crablet-event-poller
  Shared polling, progress tracking, leader election, LISTEN/NOTIFY wakeup, shared-fetch.

crablet-views
  Materialized read models built on crablet-event-poller.

crablet-automations
  Event-driven command/reaction handlers built on crablet-event-poller.

crablet-outbox
  Reliable external publication built on crablet-event-poller.

crablet-metrics-micrometer
  Optional metrics auto-collection.

crablet-test-support
  Shared test utilities for handler and integration tests.

shared-examples-domain
  Wallet and course example domains used by framework tests.

examples/wallet-example-app
examples/course-example-app
  Runnable Spring Boot examples, outside the reactor.

embabel-codegen
  AI-first CLI/MCP code generator for Crablet applications; provider-neutral through Embabel.

templates/crablet-app
  Starter project template wired for embabel-codegen MCP and Makefile/CLI workflows.
```

Module dependencies:

- `crablet-eventstore`: no dependencies on other Crablet modules.
- `crablet-commands`: depends on `crablet-eventstore`.
- `crablet-commands-web`: depends on `crablet-commands` plus web APIs.
- `crablet-event-poller`: depends on `crablet-eventstore`.
- `crablet-views`: depends on `crablet-eventstore` and `crablet-event-poller`.
- `crablet-outbox`: depends on `crablet-eventstore` and `crablet-event-poller`.
- `crablet-automations`: depends on `crablet-eventstore`, `crablet-event-poller`, and `crablet-commands`.

## Repo Conventions

- Never use fully qualified class names inline in Java code. Add imports.
- Never call `Instant.now()` directly. Inject `ClockProvider` and call `clockProvider.now()`.
- Use `EventType.type(Class)` for event type names.
- Use snake_case tag keys; tag keys are normalized to lowercase and tag values remain case-sensitive.
- Prefer domain-specific query pattern helpers for reused decision models.
- When changing **docs/event-model-renderer.js** or describing a canonical actor board, align with **`/crablet-diagram-advisor`** and **`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`**.
- When changing docs or diagrams, use Event Modeling vocabulary consistently: rows are semantic element layers; lanes are subsystem or bounded-context groupings; time flows left to right.

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
- Codegen: `embabel-codegen/README.md`
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
