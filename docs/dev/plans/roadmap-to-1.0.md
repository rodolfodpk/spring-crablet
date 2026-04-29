# Roadmap to 1.0

Agreed sequencing (April 2026): API hardening → 1.0 contract & distribution → post-1.0 cross-cutting.

**1.0 is done when:** public API surface is annotated (`@Stable`/`@Internal`), UPGRADE.md covers all user-visible breaks, and artifacts are on Maven Central. Items 1d (LISTEN/NOTIFY warning — already done) and 1e (Checkstyle parity) are Phase 1 housekeeping but not blockers for the 1.0 tag.

**Phase 2 is post-1.0** — correlation/causation propagation and the Course example are valuable but not required to ship 1.0.

_Last validated: 2026-04-29 against main (`38ee76b`). Update the hash and date whenever a phase closes or scope shifts; consider linking to a 1.0 milestone or tag once one exists._

---

## Near-term API hardening — completed

All seven pre-1.0 API quality items are done. Listed here for reference.

| # | Item | Where |
|---|------|--------|
| 1 | Remove `"open_wallet"` domain leakage | `OnDuplicate.THROW` policy on `CommandDecision.Idempotent`; `handleConcurrencyException` is fully generic |
| 2 | `CommutativeDecision` sealed interface | `CommandDecision.java` — `CommutativeCommandHandler.decide()` returns it |
| 3 | `CommutativeGuarded.withLifecycleGuard()` + construction-time validation | Factory + compact constructor validates no overlapping event types |
| 4 | `appendIdempotent(Query)` overload | `EventStore.java` |
| 5 | All `execute()` overloads return `ExecutionResult` | `CommandExecutor.java` |
| 6 | `Query.noCondition()` + `Query.empty()` disambiguation | `Query.java` |
| 7 | `storeCommand`/`hasConflict` removed from `EventStore` | `CommandAuditStore` is a separate interface; guard uses `project()` directly |

---

## Phase 1 — 1.0 contract

### 1a. `@Stable` / `@Internal` annotations

Define `@Stable` and `@Internal` marker annotations in `crablet-eventstore` — the root module that all others depend on, so the annotation type is available everywhere without a separate `crablet-annotations` artifact. Alternatively, a dedicated zero-dependency `crablet-annotations` module is a clean option if the annotation needs to be usable outside the framework; either choice is fine as long as it is decided before Central publication.

Apply `@Stable` to public types in their home modules (the annotation travels with the type, not with `crablet-eventstore`). This is a **minimal first increment** covering the primary user-facing contracts; remaining modules (`crablet-event-poller`, `crablet-commands-web`, `crablet-metrics-micrometer`) can broaden coverage in patch releases after 1.0.

Core set for the 1.0 tag:
- `crablet-eventstore`: `EventStore`, `AppendEvent`, `StoredEvent`, `Query` / `QueryBuilder`, `StreamPosition`, `Tag`, `StateProjector`, `ProjectionResult`
- `crablet-commands`: `CommandDecision`, `CommandHandler` sub-interfaces, `CommandExecutor`
- `crablet-views`: `ViewProjector`, `AbstractTypedViewProjector`
- `crablet-automations`: `AutomationHandler`
- `crablet-outbox`: `OutboxPublisher`

Apply `@Internal` (or move to `.internal` packages) to implementation classes only — **not** to public tuning beans that users inject (e.g. `EventStoreConfig`, `EventPollerConfig`):
- `CommandExecutorImpl`, `EventStoreImpl`, `EventRepositoryImpl`
- `*AutoConfiguration` classes and other Spring Boot wiring internals

No behavior changes — documentation and policy.

### 1b. Upgrade guide completeness — done

Full `main` history audited. Eight breaking changes now documented in `UPGRADE.md` (newest first): `TopicPublisherPair` package move, `StreamPosition.of(long)` removal, `WriteDataSource`/`ReadDataSource` typed beans, `notifications.enabled` property removal, `CommandHandler.Decision` record removal, `CommutativeDecision` return type narrowing, `OnDuplicate` policy on `Idempotent`, `appendIdempotent(Query)` overload, plus existing entries for `AutomationHandler react()→decide()`, metrics renames, `AutomationSubscription` removal, shared-fetch V14 migration, and `EventHandler` DataSource constructor removal.

### 1c. Maven Central publication

- Add `nexus-staging-maven-plugin` (or equivalent) + GPG signing to root POM
- Wire GPG key + Sonatype credentials in CI
- Verify BOM / dependency coordinates match what the starter template and `embabel-codegen` expect
- Publish snapshot to Central snapshots first; validate `crablet-app` template resolves it

**Before tagging 1.0.0** (release checklist — must be done alongside or before 1c):
- Semantic versioning policy documented (PATCH vs MINOR vs MAJOR once `@Stable` exists)
- Java 25 / Spring Boot 4 baseline stated explicitly in README and release notes
- Supported PostgreSQL version bounds stated in README (currently tested against PostgreSQL 17+)
- SBOM generated via `cyclonedx-maven-plugin` and attached to the release
- `UPGRADE.md` complete (see 1b)
- `@Stable` / `@Internal` applied (see 1a)
- Stale Javadoc referencing `CommandAuditStore` concerns cleaned up — **done** (`788808d`, `57d0231`)

### 1d. LISTEN/NOTIFY pooler warning — done

`PostgresNotifyWakeupSource` now emits an actionable warning when `unwrap(PGConnection.class)` fails (the symptom of routing through a pooler): message names PgBouncer transaction mode, PgCat, and RDS Proxy by name and tells the operator to point `jdbc-url` at a direct PostgreSQL connection. Other `SQLException`s get a generic "falling back to scheduled polling" message. No URL heuristics needed — the failure is caught at the point it actually happens.

### 1e. Checkstyle parity

- Add `crablet-test-support`, `shared-examples-domain`, `wallet-example-app` to the existing Checkstyle import-style gate
- Run in report mode first to get the violation diff, fix, then enforce in CI
- **Not a 1.0 semantic blocker** — developer-experience consistency, not API stability. Can trail Central by a patch release if violation count is large.

---

## Phase 2 — cross-cutting

### 2a. Correlation/causation end-to-end

`crablet-commands-web` captures the incoming header and sets `CorrelationContext`. `AutomationDispatcher` already binds `CorrelationContext` during dispatch (`CAUSATION_ID` from `event.position()`, `CORRELATION_ID` from `event.correlationId()`). Remaining work:
- **Views** — no equivalent binding exists in the view dispatch path; `ViewProjector` implementations that need traceability have no `CorrelationContext` in scope; document the pattern and add binding if warranted
- **Outbox** — `OutboxPublisher.publishBatch` already receives `StoredEvent` carrying `correlationId`/`causationId`; no API change needed — remaining work is envelope conventions (which fields to forward in HTTP/Kafka payloads)
- **Integration test** — verify IDs survive the full command → view → outbox path

### 2b. Second complete example (Course domain)

`shared-examples-domain` already has Course domain logic. Remaining work:
- Wire into `wallet-example-app` (or a sibling `course-example-app`) with Flyway migrations, view projectors, an automation, and HTTP endpoints
- Gives multi-aggregate constraint testing (course capacity + student subscription) a real runnable home

**Note on ordering:** 2a's integration test is simpler to write once 2b's richer app trail exists — the Course example exercises more module interactions (views + automations + outbox) than the current wallet app. If capacity is limited, 2b first is the lower-risk order.
