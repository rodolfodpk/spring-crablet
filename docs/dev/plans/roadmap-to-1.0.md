# Roadmap to 1.0

Agreed sequencing (April 2026): API hardening → 1.0 contract & distribution → post-1.0 cross-cutting.

**1.0 is done when:** public API surface is annotated (`@Stable`/`@Internal`), UPGRADE.md covers all user-visible breaks, and artifacts are on Maven Central. Items 1d (LISTEN/NOTIFY warning — already done) and 1e (Checkstyle parity) are Phase 1 housekeeping but not blockers for the 1.0 tag.

**Phase 2 is post-1.0** — correlation/causation propagation and the Course example are valuable but not required to ship 1.0.

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

### 1a. `@Stable` / `@Internal` annotations — done

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

**Blocked on account setup (no code work until this is done):**
1. Register at central.sonatype.com and claim the `com.crablet` namespace (DNS TXT record or GitHub repo verification)
2. Generate a GPG key pair for artifact signing

**Then — POM + CI work:**
- Add `central-publishing-maven-plugin` + `maven-gpg-plugin` to root POM
- Wire GPG key + Sonatype credentials as CI secrets
- Verify `groupId`/`artifactId`/version in all module POMs match what the starter template and `crablet-codegen` reference
- Publish a snapshot to Central snapshots first; validate `crablet-app` template resolves it before tagging 1.0.0

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

### 1e. Checkstyle parity — done

- Add `crablet-test-support`, `shared-examples-domain`, `examples/wallet-example-app` to the existing Checkstyle import-style gate
- Run in report mode first to get the violation diff, fix, then enforce in CI
- **Not a 1.0 semantic blocker** — developer-experience consistency, not API stability. Can trail Central by a patch release if violation count is large.

---

## Phase 2 — cross-cutting

### 2a. Correlation/causation end-to-end

`crablet-commands-web` captures the incoming header and sets `CorrelationContext`. `AutomationDispatcher` already binds `CorrelationContext` during dispatch (`CAUSATION_ID` from `event.position()`, `CORRELATION_ID` from `event.correlationId()`). Remaining work:
- **Views** — no equivalent binding exists in the view dispatch path; `ViewProjector` implementations that need traceability have no `CorrelationContext` in scope; document the pattern and add binding if warranted
- **Outbox** — `OutboxPublisher.publishBatch` already receives `StoredEvent` carrying `correlationId`/`causationId`; no API change needed — remaining work is envelope conventions (which fields to forward in HTTP/Kafka payloads)
- **Integration test** — verify IDs survive the full command → view → outbox path

### 2b. Examples consolidation + Course domain app — done

**Repo layout (done):**
```
examples/
  wallet-example-app/     ← moved from root
  course-example-app/     ← new (done)
shared-examples-domain/   ← stays at root (used in test scope by framework modules)
```

**Step 1 — Move `wallet-example-app` into `examples/` — done**

**Step 2 — Create `course-example-app` — done**
- Spring Boot app under `examples/course-example-app/`
- Depends on framework modules + `shared-examples-domain`
- `make` targets: `make course-start`, `make course-dev`
- All source files implemented: `CourseApplication`, `CourseAvailabilityViewProjector`, `CourseCapacityAutomation`, `CourseQueryController`, Flyway migrations, `CrabletConfig`
- Pending: commit to git (`examples/course-example-app/` is currently untracked)

Module coverage:

| Module | What it exercises |
|---|---|
| `crablet-eventstore` | Multi-entity DCB: one decision model spans course + student (unique to this app — wallet doesn't show this) |
| `crablet-commands` | 3 handlers from `shared-examples-domain`: `DefineCourse`, `ChangeCourseCapacity`, `SubscribeStudentToCourse` |
| `crablet-commands-web` | Generic HTTP command API + Swagger |
| `crablet-views` | Course availability view (capacity, enrolled count, seats remaining) |
| `crablet-automations` | React to `StudentSubscribedToCourse` — notify when course reaches capacity |
| `crablet-outbox` | Publish enrollment events to external systems |
| `crablet-metrics-micrometer` | Implicit via auto-config |

The **key differentiator**: `SubscribeStudentToCourse` checks both the course's remaining capacity AND the student's subscription limit in a single DCB consistency boundary — the multi-aggregate constraint the wallet doesn't demonstrate.

**Why this ordering matters for 2a:** the Course app exercises views + automations + outbox together, giving the correlation/causation integration test a richer and more realistic trail than the wallet app alone.

**Note on ordering:** 2a's integration test is simpler to write once 2b exists. Do 2b first.
