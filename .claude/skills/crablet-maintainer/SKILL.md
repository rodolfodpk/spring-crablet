---
name: crablet-maintainer
description: >
  Use this skill for spring-crablet framework work: changing framework modules,
  public APIs, eventstore append internals, command executor behavior, poller or
  shared-fetch routing, auto-configuration, module tests, starter templates,
  embabel-codegen internals, or docs that describe framework behavior. Do not use
  for application feature-slice implementation in generated Crablet apps.
---

# Crablet Framework Maintenance

This skill is for contributors changing the spring-crablet framework repository.

## Routing

- Framework code changes use this skill, including DCB implementation changes.
- Choosing or diagnosing DCB for an application command handler uses `dcb`.
- Building generated applications or feature slices uses `crablet-app-dev`.
- Workshop-level `event-model.yaml` design uses `event-modeling`.

## Maintainer Sources

Prefer linking to source docs over duplicating full architecture prose:

- Framework development docs: `docs/dev/README.md`
- Build instructions: `docs/user/BUILD.md`
- Module reference: `docs/user/MODULES.md`
- EventStore: `crablet-eventstore/README.md`
- Commands: `crablet-commands/README.md`
- Event poller: `crablet-event-poller/README.md`
- Views: `crablet-views/README.md`
- Outbox: `crablet-outbox/README.md`
- AI-first tooling: `embabel-codegen/README.md`
- Starter template: `templates/crablet-app/README.md`

## Current Decisions

Treat these as current repository policy unless the change explicitly revises them:

- `AutomationHandler` is the single public automation contract; `AutomationSubscription` has been removed.
- Automations decide commands/reactions. Reliable external publication belongs in `crablet-outbox`.
- `crablet-event-poller` owns shared event matching and per-instance override abstractions: `EventSelection`, `EventSelectionSqlBuilder`, `EventSelectionMatcher`, `ProcessorRuntimeOverrides`, and `ProcessorRuntimeOverrideResolver`.
- Views, automations, and outbox topics expose the same `EventSelection` mental model: `eventTypes`, `requiredTags`, `anyOfTags`, and `exactTags`; empty dimensions are unrestricted, dimensions combine with AND, legacy fetch applies the selection in SQL, and shared-fetch applies the same selection during in-memory routing.
- Generic poller handlers should not accept raw `DataSource`.
- View projection writes are owned by `crablet-views`, not by the generic poller contract.
- Poller-backed modules model global module defaults plus per-processor configuration.
- Shared-fetch mode is opt-in per module and requires schema V14.
- LISTEN wakeup uses `crablet.event-poller.notifications.jdbc-url` and must be a direct Postgres connection.
- The eventstore sends NOTIFY after every append; there is no separate eventstore flag.
- `crablet-commands-web` is server-agnostic at runtime and depends only on `jakarta.servlet-api`.
- The root tutorial is a tutorial series under `docs/user/tutorials/`.

## DataSource Rules

Crablet exposes two datasource roles:

| Layer | DataSource |
|-------|------------|
| Command appends / `EventStore` | `WriteDataSource` |
| Progress tracking | `WriteDataSource` |
| Leader election | `WriteDataSource` |
| View projection writes | `WriteDataSource` |
| Event fetching for views, automations, outbox | `ReadDataSource` |

Do not inject `ReadDataSource` into `AbstractViewProjector` or `AbstractTypedViewProjector`.
Projection writes must go to the primary.

## Public Contract Guidance

- Keep public APIs small and explicit.
- Use imports instead of fully qualified class names in Java code.
- Use `ClockProvider.now()` instead of `Instant.now()`.
- Tags are snake_case and normalized to lowercase; values remain case-sensitive.
- Use `EventType.type(Class)` for type-safe event names.
- Prefer domain-specific query pattern helpers for repeated decision models.

## Codegen And Template Policy

- Codegen agents depend on `CodegenLlmClient`, not provider SDKs or concrete provider services.
- Provider SDK and Embabel provider factory references belong inside `com.crablet.codegen.llm`
  adapter code and focused tests.
- Keep `codegen.anthropic.*` / `ANTHROPIC_API_KEY` backward compatibility while documenting
  provider-neutral `codegen.llm.*` / `CODEGEN_LLM_*` configuration.
- Generated command-handler artifacts are Java interfaces with empty bodies. User `@Component` implementation classes provide logic.
- Generated automation handler interfaces should contain metadata defaults only.
- Generated outbox publisher interfaces should contain metadata defaults only.
- `OutboxPublisher.PublishMode` is an inherited nested enum; do not import a standalone `com.crablet.outbox.PublishMode`.
- Starter template generation must target `src/main/java`, not the project root.

## Build Graph And Tests

- `shared-examples-domain`, `examples/wallet-example-app`, and `examples/course-example-app` are excluded from the reactor.
- Use `make install` for the normal full build because it applies the required build order and stub JAR steps.
- Use `make install-all-tests` when integration coverage is required.
- Use `./mvnw test -pl <module-name>` only when the module dependencies are already built.
- Tests use Testcontainers for PostgreSQL integration tests.
- Framework modules should use `shared-examples-domain` in test scope for realistic scenarios where useful.
- Shared test utilities live in `crablet-test-support`.

## Root vs Template Copy Policy

Root skill copies (`/.claude/skills/`) are maintainer-aware. They may reference:
- Framework and runtime semantics
- Codegen artifact planning
- Docs renderer rules and renderer internals
- Template consistency policy
- DCB correctness at the framework level
- Automation/outbox boundaries

Template skill copies (`templates/crablet-app/.claude/skills/`) must stay app-user focused:
- No renderer internals (arrow stroke behavior, canvas constants, docs-authoring details)
- No framework module change guidance
- No codegen adapter internals
- Only user-facing behaviors that affect the running app

When a root user-facing section changes, mirror the relevant subset into the template copy.
When a framework-only or renderer-only section changes, do not copy it into the template.
The template skill header should say "this Crablet app" rather than "a Crablet app" or
"the framework repo."

## Maintainer Checklist

When changing framework behavior:

- Identify which public API, auto-configuration, or migration contract changes.
- Update module README or user docs when behavior visible to application teams changes.
- Add or adjust module tests at the nearest layer that proves the behavior.
- If poller behavior changes, check views, automations, and outbox because they share the infrastructure.
- If eventstore append behavior changes, check command executor decisions and DCB diagnostics.
- If template or codegen behavior changes, check both `embabel-codegen` docs and `templates/crablet-app`.
