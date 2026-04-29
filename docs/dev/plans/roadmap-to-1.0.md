# Roadmap to 1.0 and beyond

Agreed sequencing (April 2026): API hardening → 1.0 contract & distribution → ops & codegen maturity → cross-cutting.

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

Define a `@Stable` marker annotation in `crablet-eventstore` (the root module everyone depends on).

Apply `@Stable` to:
- `EventStore`, `CommandDecision`, `CommandHandler` sub-interfaces, `CommandExecutor`
- `AppendEvent`, `StoredEvent`, `Query` / `QueryBuilder`, `StreamPosition`, `Tag`
- `StateProjector`, `ProjectionResult`
- `ViewProjector` / `AbstractTypedViewProjector`
- `AutomationHandler`, `OutboxPublisher`

Apply `@Internal` (or move to `.internal` packages) to:
- `CommandExecutorImpl`, `EventStoreImpl`, `EventRepositoryImpl`
- All `*Config` internals

No behavior changes — documentation and policy.

### 1b. Upgrade guide completeness

Audit `UPGRADE.md` against `git log` since the earliest SNAPSHOT tag. Additions needed at minimum:
- `appendIdempotent(Query)` overload
- `OnDuplicate` policy on `Idempotent`
- `CommutativeDecision` return type narrowing on `CommutativeCommandHandler`

### 1c. Maven Central publication

- Add `nexus-staging-maven-plugin` (or equivalent) + GPG signing to root POM
- Wire GPG key + Sonatype credentials in CI
- Verify BOM / dependency coordinates match what the starter template and `embabel-codegen` expect
- Publish snapshot to Central snapshots first; validate `crablet-app` template resolves it

### 1d. Checkstyle parity

- Add `crablet-test-support`, `shared-examples-domain`, `wallet-example-app` to the existing Checkstyle import-style gate
- Run in report mode first to get the violation diff, fix, then enforce in CI

---

## Phase 2 — ops story

### 2a. LISTEN/NOTIFY ergonomics

- Add startup pooler detection: if `notifications.jdbc-url` looks like a PgBouncer/RDS Proxy/PgCat URL, log a warning at boot time
- `CONFIGURATION.md` already recommends 30 s polling when LISTEN wakeup is active; no interval-guidance changes needed

### 2b. ECS/Fargate spike (time-boxed)

- Extend `embabel-codegen` with a `--target=ecs` flag generating a minimal ECS task definition + service JSON mirroring the existing K8s topology (command API pod vs singleton worker pod)
- Document what emulates well with MiniStack vs what needs a real AWS account
- Outcome decides whether to continue to Terraform/CDK or park it

---

## Phase 3 — cross-cutting

### 3a. Correlation/causation end-to-end

`crablet-commands-web` already captures the header and sets `CorrelationContext`. Remaining work:
- Propagate to `ViewProjector` (pass correlation through `StoredEvent` → view write)
- `OutboxPublisher.publishBatch` already receives `StoredEvent` which carries `correlationId`/`causationId`; no API change needed
- Remaining work: document envelope conventions (which fields to forward in HTTP/Kafka payloads) and add an integration test verifying the IDs survive the full command → view → outbox path

### 3b. Second complete example (Course domain)

`shared-examples-domain` already has Course domain logic. Remaining work:
- Wire into `wallet-example-app` (or a sibling `course-example-app`) with Flyway migrations, view projectors, an automation, and HTTP endpoints
- Gives multi-aggregate constraint testing (course capacity + student subscription) a real runnable home

---

## Versioning and compatibility notes

Items to address before advertising 1.0 stability to external consumers:

- **Semantic versioning policy** — define what qualifies as PATCH vs MINOR vs MAJOR once `@Stable` exists
- **Java 25 / Spring Boot 4 baseline** — state explicitly; sets expectations for adoption audience
- **SBOM / dependency policy** — light touch: `cyclonedx-maven-plugin` at release + declared dependency update cadence
