# Test-Support Split: Fast BDD vs Real-Postgres Integration — Plan

Status: proposed (not started)
Created: 2026-05-31
Relates to: PRODUCT_ROADMAP.md › Horizon 1 §1.2 (API hardening), `/crablet-test-authoring`

## Goal

Organize test infrastructure by **test type / dependency weight** so that:

- **BDD / handler unit tests** are fast and depend on an in-memory event store only — **no PostgreSQL, no Testcontainers, no Flyway**.
- **Integration tests** use the real PostgreSQL event store via Testcontainers.

…while keeping the module graph free of *new* cyclic dependencies.

## Problem (current state)

`crablet-test-support` bundles two infrastructures with very different footprints in one module:

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

## Target module layout

```
                          FAST (no Postgres)                 HEAVY (Testcontainers/Postgres)
eventstore ── crablet-test-support          ───────────────  crablet-test-support-it
                 InMemoryEventStore                              AbstractCrabletTest (+ cleanup, config)
                 deps: crablet-eventstore only                   deps: crablet-eventstore + Testcontainers/PG/Flyway
                       │
commands ── crablet-commands-test-support   (FAST)
                 AbstractHandlerUnitTest
                 deps: crablet-commands + crablet-test-support(fast) + junit-jupiter-api + assertj
                 (NO Postgres / Testcontainers)
```

Consumer story:

- **BDD handler tests** → depend on `crablet-commands-test-support` (fast, no Docker).
- **Integration tests** → depend on `crablet-test-support-it` (real Postgres).
- **Eventstore-level unit fakes** → depend on `crablet-test-support` (fast).

## What moves where

| Artifact | From | To |
|----------|------|----|
| `InMemoryEventStore` | `crablet-test-support` | stays in `crablet-test-support` (now Postgres-free) |
| `AbstractCrabletTest`, `cleanup/`, `config/` | `crablet-test-support` | new `crablet-test-support-it` |
| `AbstractHandlerUnitTest` | `crablet-commands` test sources | new `crablet-commands-test-support` (main sources, e.g. package `com.crablet.command.test`) |
| 7 example-handler BDD tests (`OpenWalletCommandHandlerUnitTest`, courses/wallet `*UnitTest`) | `crablet-commands` test sources | `shared-examples-domain` test sources |
| dead `InMemoryEventStore` duplicate | `crablet-commands` test sources | **delete** |

## Dependency / cycle analysis

- `crablet-test-support` (fast) → `crablet-eventstore` only. Leaf above eventstore.
- `crablet-test-support-it` → `crablet-eventstore` (+ Testcontainers/PG/Flyway). Does **not** need `crablet-commands`, so `crablet-commands → crablet-test-support-it` (integration tests, test scope) has **no back-edge**. ✅
- `crablet-commands-test-support` → `crablet-commands` + `crablet-test-support`(fast). Consumed at test scope by `shared-examples-domain` and apps. **No cycle once the 7 example-handler BDD tests leave `crablet-commands`** — otherwise `commands → commands-test-support → commands` reappears. ✅
- Pre-existing `eventstore ↔ test-support` cycle (handled today via staged Makefile builds) is **out of scope** here; the fast/heavy split shrinks its blast radius but does not remove it.

## Migration steps (ordered)

1. Create `crablet-test-support-it`; move `AbstractCrabletTest` + `cleanup/` + `config/` into it; give it the Testcontainers/Postgres/Flyway deps.
2. Strip Testcontainers/Postgres/Flyway from `crablet-test-support`; it keeps `InMemoryEventStore` and depends on `crablet-eventstore` only.
3. Repoint every current integration-test consumer of `AbstractCrabletTest` from `crablet-test-support` → `crablet-test-support-it` (test scope).
4. Create `crablet-commands-test-support`; move `AbstractHandlerUnitTest` into its **main** sources (package `com.crablet.command.test`).
5. Move the 7 example-handler BDD tests from `crablet-commands` → `shared-examples-domain` test sources; point them at `crablet-commands-test-support`.
6. Delete the orphaned `InMemoryEventStore` duplicate in `crablet-commands`.
7. Add `crablet-test-support`, `crablet-test-support-it`, and `crablet-commands-test-support` to BOM `dependencyManagement` so consumers drop explicit versions.
8. Repoint the already-pending consumers (template `pom.xml`, loan snapshot `pom.xml`, the loan handler unit test) from the `crablet-commands` test-jar → `crablet-commands-test-support`.
9. Verify the `crablet-commands` test-jar still has a real consumer; if the 3 current references are vestigial, drop the `test-jar` goal.
10. Update `/crablet-test-authoring` and `AI_SKILLS.md` to point at the new modules; update CLAUDE.md module map + dependency list.
11. Update the Makefile staged-build targets and `make test-pl` notes for the new modules.

## Open decisions

- **Naming:** `crablet-test-support-it` vs `crablet-test-support-integration`; `crablet-commands-test-support` vs `crablet-commands-test-fixtures`.
- **`@Stable` governance:** mark `AbstractHandlerUnitTest` (and the fast `InMemoryEventStore`) as stable test API for 1.0.
- **Home for relocated example-handler tests:** `shared-examples-domain` test sources (recommended — tests next to the handlers) vs a dedicated test module.
- **Pre-existing `eventstore ↔ test-support` cycle:** address now (separate `AbstractCrabletTest` consumers) or defer.

## Out of scope

- Removing the pre-existing `eventstore ↔ test-support` cycle.
- Any change to the runtime modules' main APIs.
- Generating handler unit tests from codegen (separate question).
