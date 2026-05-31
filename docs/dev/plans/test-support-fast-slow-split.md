# Test-Support Split: Fast BDD vs Real-Postgres Integration — Plan

Status: proposed (not started)
Created: 2026-05-31
Relates to: PRODUCT_ROADMAP.md › Horizon 1 §1.3 (API hardening), `/crablet-test-authoring`

## Goal

Organize test infrastructure by **test type / dependency weight** so that:

- **BDD / handler unit tests** are fast and depend on an in-memory event store only — **no PostgreSQL, no Testcontainers, no Flyway**.
- **Integration tests** use the real PostgreSQL event store via Testcontainers.

…while keeping the module graph free of *new* cyclic dependencies.

## Problem (current state)

Today there is one shared module, `crablet-test-support`, that bundles two infrastructures with very different footprints:

| Class | Footprint |
|-------|-----------|
| `InMemoryEventStore` | imports only `com.crablet.eventstore.*` interfaces — genuinely fast |
| `AbstractCrabletTest` (+ `cleanup`, `config`) | needs Testcontainers + Postgres driver + Flyway |

Because both live in `crablet-test-support`, its **compile** dependencies include `testcontainers-postgresql`, `postgresql`, `flyway-core`, `flyway-database-postgresql`. Consequences:

1. **Fast BDD consumers pay the integration tax.** Anyone depending on `crablet-test-support` for the in-memory fake transitively drags in the whole Testcontainers/Postgres/Flyway stack.
2. **The handler BDD base is trapped in `crablet-commands` test sources.** `AbstractHandlerUnitTest` (the given/when/then base) is published only via the `crablet-commands` **test-jar**, which ships **45 classes** (framework-internal tests included) and is not `@Stable`-governed — a leaky external surface.
3. **Version friction.** Neither the commands test-jar variant nor `crablet-test-support` is in the BOM `dependencyManagement`, so external consumers must hardcode `${crablet.version}`.
4. **Dead duplicate.** `crablet-commands/src/test/java/com/crablet/command/handlers/unit/InMemoryEventStore.java` is orphaned (nothing references it; `AbstractHandlerUnitTest` imports the test-support one). Delete it.

## Principle

Partition by the axis that matters — **fast (in-memory) vs heavy (real Postgres)** — which maps cleanly onto module boundaries. This supersedes an earlier "split by layer" idea; same module count, organized by the useful axis.

## Target module names

A consistent `crablet-test-*` family so the test-support modules group together, with each qualifier naming what the module provides:

| Module | Provides | Footprint |
|--------|----------|-----------|
| `crablet-test-inmemory` | `InMemoryEventStore` (+ fast fixtures) | **fast** — `crablet-eventstore` only, no Postgres |
| `crablet-test-postgres` | `AbstractCrabletTest` (+ `cleanup`, `config`) | heavy — Testcontainers / Postgres / Flyway |
| `crablet-test-commands` | `AbstractHandlerUnitTest` (BDD handler base) | fast — `crablet-commands` + `crablet-test-inmemory`, no Postgres |

`crablet-test-inmemory` is the renamed-and-slimmed successor to today's `crablet-test-support`; `crablet-test-postgres` is the Testcontainers part extracted out of it.

## Target module layout

```
                          FAST (no Postgres)                 HEAVY (Testcontainers/Postgres)
eventstore ── crablet-test-inmemory         ───────────────  crablet-test-postgres
                 InMemoryEventStore                              AbstractCrabletTest (+ cleanup, config)
                 deps: crablet-eventstore only                   deps: crablet-eventstore + Testcontainers/PG/Flyway
                       │
commands ── crablet-test-commands           (FAST)
                 AbstractHandlerUnitTest
                 deps: crablet-commands + crablet-test-inmemory + junit-jupiter-api + assertj
                 (NO Postgres / Testcontainers)
```

Consumer story:

- **BDD handler tests** → depend on `crablet-test-commands` (fast, no Docker).
- **Integration tests** → depend on `crablet-test-postgres` (real Postgres).
- **Eventstore-level unit fakes** → depend on `crablet-test-inmemory` (fast).

## What moves where

| Artifact | From | To |
|----------|------|----|
| `InMemoryEventStore` | `crablet-test-support` | `crablet-test-inmemory` (renamed module; now Postgres-free) |
| `AbstractCrabletTest`, `cleanup/`, `config/` | `crablet-test-support` | new `crablet-test-postgres` |
| `AbstractHandlerUnitTest` | `crablet-commands` test sources | new `crablet-test-commands` (main sources, e.g. package `com.crablet.test.commands`) |
| 7 example-handler BDD tests (`OpenWalletCommandHandlerUnitTest`, courses/wallet `*UnitTest`) | `crablet-commands` test sources | `shared-examples-domain` test sources |
| dead `InMemoryEventStore` duplicate | `crablet-commands` test sources | **delete** |

## Dependency / cycle analysis

- `crablet-test-inmemory` → `crablet-eventstore` only. Leaf above eventstore.
- `crablet-test-postgres` → `crablet-eventstore` (+ Testcontainers/PG/Flyway). Does **not** need `crablet-commands`, so `crablet-commands → crablet-test-postgres` (integration tests, test scope) has **no back-edge**. ✅
- `crablet-test-commands` → `crablet-commands` + `crablet-test-inmemory`. Consumed at test scope by `shared-examples-domain` and apps. **No cycle once the 7 example-handler BDD tests leave `crablet-commands`** — otherwise `commands → crablet-test-commands → commands` reappears. ✅
- Pre-existing `eventstore ↔ test-support` cycle (handled today via staged Makefile builds) is **out of scope** here; the fast/heavy split shrinks its blast radius but does not remove it. After the rename it is `eventstore ↔ crablet-test-inmemory`.

## Migration steps (ordered, green at each step)

1. Create `crablet-test-postgres`; move `AbstractCrabletTest` + `cleanup/` + `config/` into it; give it the Testcontainers/Postgres/Flyway deps.
2. Rename `crablet-test-support` → `crablet-test-inmemory`; strip Testcontainers/Postgres/Flyway; it keeps `InMemoryEventStore` and depends on `crablet-eventstore` only.
3. Repoint every current integration-test consumer of `AbstractCrabletTest` from the old module → `crablet-test-postgres` (test scope).
4. Create `crablet-test-commands`; move `AbstractHandlerUnitTest` into its **main** sources (package `com.crablet.test.commands`).
5. Move the 7 example-handler BDD tests from `crablet-commands` → `shared-examples-domain` test sources; point them at `crablet-test-commands`.
6. Delete the orphaned `InMemoryEventStore` duplicate in `crablet-commands`.
7. Add `crablet-test-inmemory`, `crablet-test-postgres`, and `crablet-test-commands` to BOM `dependencyManagement` so consumers drop explicit versions.
8. Wire the BDD base into consumers via `crablet-test-commands` (test scope): the starter template, the loan snapshot (re-add its handler unit test against the new module), and any app that writes handler unit tests.
9. Verify the `crablet-commands` test-jar still has a real consumer; if the 3 current references are vestigial, drop the `test-jar` goal.
10. Update `/crablet-test-authoring` and `AI_SKILLS.md` to point at the new modules; update CLAUDE.md module map + dependency list.
11. Update the Makefile staged-build targets and `make test-pl` notes for the new modules.

## Resolved decisions

- **Module names:** `crablet-test-inmemory` / `crablet-test-postgres` / `crablet-test-commands` (consistent `crablet-test-*` family).
- **Home for relocated example-handler tests:** `shared-examples-domain` test sources (tests next to the handlers).

## Open decisions

- **`@Stable` governance:** mark `AbstractHandlerUnitTest` (and `InMemoryEventStore`) as stable test API for 1.0.
- **Pre-existing `eventstore ↔ crablet-test-inmemory` cycle:** address now (separate `AbstractCrabletTest` consumers) or defer.

## Out of scope

- Removing the pre-existing `eventstore ↔ test-support` cycle.
- Any change to the runtime modules' main APIs.
- Generating handler unit tests from codegen (separate question).
