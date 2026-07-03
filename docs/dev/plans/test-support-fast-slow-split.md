# Test-Support: Fast BDD Base + (Optional) In-Memory Extraction

Status: M1 done; full split **dropped**; optional minimal extraction documented below.
Created: 2026-05-31
Relates to: PRODUCT_ROADMAP.md › Horizon 1 §1.3, `/crablet-test-authoring`

## What was done (M1)

Extracted the command-handler BDD base into its own module so handler unit tests are fast and
independent of the framework's internal test-jar:

- New module **`crablet-test-commands`** (standalone, built via Makefile staging like
  `crablet-test-support`). Depends on `crablet-commands` + `crablet-test-support` + junit-jupiter-api + assertj.
- `AbstractHandlerUnitTest` → moved into it as **`AbstractInMemoryHandlerTest`** (package `com.crablet.test.commands`), backed by `InMemoryEventStore` (no Postgres).
- `crablet-commands`' 7 example-handler unit tests repointed to the new base (kept in place; the staged build handles the `commands ↔ test-commands` ordering via stubs, same as `eventstore ↔ test-support`).
- Deleted the orphaned duplicate `InMemoryEventStore` in `crablet-commands` test sources.
- Makefile: added `build-test-commands` stage + stub; wired into `install` / `install-all-tests` / `ci-verify`.

This delivers the actual goal: **handler BDD tests run fast against the in-memory event store and never require a running PostgreSQL.**

## Why the full split was dropped

The original plan split `crablet-test-support` into `crablet-test-inmemory` + `crablet-test-postgres`, renamed the PostgreSQL integration base, relocated the DB migrations, reworked `check-migration-sync` / `check-test-support-artifact`, and repointed usages across four modules.

On review, that buys only **dependency hygiene**, not speed:

- BDD test *speed* is already achieved by M1 — `InMemoryEventStore` starts no container; Testcontainers only runs when a test extends `AbstractPostgresEventStoreTest`. Having Testcontainers on the classpath does not slow or gate a BDD test.
- The full split's sole remaining benefit is keeping Testcontainers/Postgres/Flyway off the BDD module's transitive classpath — high churn and build-system risk for marginal value.

Decision: **do not do the full split.** The shared PostgreSQL integration base now keeps its home in `crablet-test-support` as `AbstractPostgresEventStoreTest`.

## Optional: minimal `crablet-test-inmemory` extraction

Only worth doing if the Testcontainers/Postgres jars on the BDD classpath actually bother consumers
(e.g. a published 1.0 where pulling `crablet-test-commands` for unit tests shouldn't drag in Testcontainers).

Minimal, low-churn shape — **no rename, no migration move, no 32-usage repoint**:

1. Create `crablet-test-inmemory` containing only `InMemoryEventStore` (depends on `crablet-eventstore`).
2. `crablet-test-support` depends on `crablet-test-inmemory` (re-exports the fake) — existing `AbstractPostgresEventStoreTest` consumers unchanged.
3. `crablet-test-commands` depends on `crablet-test-inmemory` instead of `crablet-test-support` → its transitive classpath is Postgres-free.
4. Add `crablet-test-inmemory` to the Makefile staging + BOM.

## Other optional follow-ups (decoupled, only if desired)

- Rename already done: the shared PostgreSQL integration base is now `AbstractPostgresEventStoreTest`. Module-local integration bases can be renamed separately only if the local clarity benefit justifies the churn.
- Move `crablet-commands`' 7 example-handler unit tests to `shared-examples-domain` test sources ("tests next to the handlers"). Not required — the staged build handles the dependency ordering as-is.
- Add `crablet-test-commands` (and `crablet-test-inmemory`, if created) to BOM `dependencyManagement` so external app consumers drop explicit versions.
