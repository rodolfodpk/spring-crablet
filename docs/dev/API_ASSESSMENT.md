# Spring-Crablet — Public API, Design & Implementation Assessment

**Date:** 2026-05-31
**Branch reviewed:** `refactor/test-support-split`
**Scope:** Public API surface, design, implementation quality, and adoption readiness of the framework modules (`crablet-eventstore`, `crablet-commands`, `crablet-commands-web`, `crablet-event-poller`, `crablet-views`, `crablet-automations`, `crablet-outbox`, `crablet-observability`, `crablet-metrics-micrometer`).

> Method note: this is a static read of the public types, module layout, build files, docs, and test inventory. It is not a runtime/perf review and did not execute the build. Counts are from `find`/`grep` over `src/main` and `src/test` (excluding `target/`).

---

## Verdict

**The design and API quality are high — clearly above the average event-sourcing library on the JVM.** The core abstractions are small, explicit, well-documented, and encode correctness (DCB consistency strategies) into the type system rather than leaving it to convention. The framework is *technically* adoptable today.

**The gating issues are distribution and platform-currency, not design:**

1. Nothing is published — version is `1.0.0-SNAPSHOT`, root POM has **no** `distributionManagement` / signing / central-publishing config, and there is no standalone consumable BOM module (only `dependencyManagement` inside the root POM). Adopters must build from source.
2. The platform target is recent: **Spring Boot `4.0.5`** (a released version) on **Java 25**. Spring Boot 4.x being current is reasonable; the aggressive part is the Java 25 baseline, which forces adopters onto the newest JDK.

If you publish signed artifacts and a consumable BOM, this is a credible "early adopter" framework. As-is, it's "build-it-yourself / evaluation" grade.

| Dimension | Rating | Notes |
|---|---|---|
| Core API design | **Strong** | Sealed decisions, named append strategies, explicit stability contract |
| Naming & consistency | Good | A few self-convention violations; event-class-name-is-contract rule needs a guard (by design) |
| Null-safety | Fair | jspecify adopted but only 6 of 45 main packages `@NullMarked` |
| Modularity | **Strong** | Clean dependency graph, optional layers, per-module autoconfig |
| Documentation | **Strong** | Per-module READMEs + 6 progressive tutorials |
| Test depth | **Strong** | 189 framework test files; Testcontainers integration |
| Release/distribution | **Weak** | SNAPSHOT, unpublished, no consumable BOM |
| Build ergonomics | Fair | Out-of-reactor modules require `make`, not plain Maven |

---

## What's genuinely good

### 1. An explicit, enforced stability contract
`@Stable` and `@Internal` annotations exist and carry a documented semver promise ("breaking changes will not be introduced without a MAJOR version bump"; "types without `@Stable` should be treated as subject to change"). The core public types (`EventStore`, `AppendEvent`, `StreamPosition`, `Tag`, `ConcurrencyException`, `CommandHandler`, `CommandExecutor`, `CommandDecision`) are marked `@Stable`. Very few libraries at this stage are this disciplined about what they're promising. This is the single best adoption signal in the codebase.

### 2. The core `EventStore` interface is small and intention-revealing
Three append methods map 1:1 to the DCB consistency strategies instead of one overloaded `append`:
- `appendCommutative(events)` — order-independent
- `appendNonCommutative(events, decisionModel, streamPosition)` — DCB conflict check
- `appendIdempotent(...)` — entity creation, with a `Query`-based overload for multi-tag cases

Plus ergonomic `project(...)` overloads, an `exists(query)` short-circuit, and `executeInTransaction` with an explicit, well-documented transaction guarantee block. The Javadoc explains *when* to use each method with domain examples (deposits vs. withdrawals vs. `OpenWallet`).

### 3. `CommandDecision` encodes DCB semantics in the type system
`CommandDecision` is a **sealed interface** with variants (`Commutative`, `CommutativeGuarded`, `NonCommutative`, `Idempotent`, `NoOp`) that mirror the append strategies. A nested `CommutativeDecision` marker means a `CommutativeCommandHandler` *cannot* accidentally return a `NonCommutative` decision — the compiler enforces it. The executor pattern-matches the variant to pick the append method. `CommutativeGuarded` even validates *in its constructor* that the lifecycle guard query doesn't overlap the appended event types, turning a subtle correctness rule into a fail-fast `IllegalArgumentException`. This is sophisticated, correctness-first API design.

### 4. Ergonomics done well
Static factories (`of(...)`), fluent builders (`AppendEvent.builder`), backward-compatible constructors on records, and a `OnDuplicate` policy enum for idempotency. The `AppendCondition` low-level escape hatch is deliberately *not* `@Stable` and its Javadoc steers people to the semantic methods first — good layering of "easy path" vs. "advanced path."

### 5. Clean modularity & Spring integration
- 10 focused framework modules with a documented, acyclic dependency graph.
- Spring Boot autoconfiguration registered for all 8 consumer modules (`AutoConfiguration.imports` present in eventstore, commands, commands-web, event-poller, views, automations, outbox, metrics-micrometer).
- Optional layers are genuinely optional (commands, web, views, automations, outbox, metrics each stand alone on top of the eventstore).

### 6. Documentation and tests are real, not decorative
- A README in **every** module, plus **6 progressive tutorials** (`01-event-store-basics` → `06-outbox`) and a getting-started guide.
- **189** framework test files (across the 9 framework modules); integration tests use Testcontainers against real PostgreSQL. `docs-samples` compiles tutorial snippets against the live API to catch doc drift.

### 7. Modern, null-aware Java
Records, sealed types, pattern matching, Java 25. jspecify (`org.jspecify.annotations`) is adopted for nullability rather than the ambiguous JSR-305 annotations.

---

## Concerns, prioritized

### P0 — Distribution / release maturity *(adoption gate)*
- Version `1.0.0-SNAPSHOT`; no released artifact. Root POM has **no** `distributionManagement`, GPG signing, or central-publishing plugin — there is no path for a consumer to `mvn`-pull this.
- No standalone, consumable BOM artifact — version alignment lives in the root POM's `dependencyManagement`, which downstream projects can't easily import.
- Platform: **Spring Boot `4.0.5`** on **Java 25**. Spring Boot 4 is fine; the Java 25 baseline is the aggressive constraint and should be a deliberate, documented support decision.
- **Recommendation:** publish a standalone `crablet-bom`, wire up signed Maven Central publishing, document the Java/Spring support matrix, and cut a real `1.0.0` (or an honest `1.0.0-M1`).

### P1 — The "event class name is a durable contract" rule needs to be explicit and guarded *(by design)*
`EventType.type(Class)` returns `Class.getSimpleName()` **intentionally**: the event-type string is derived from — and tied to — the Java class name, and it must stay stable for the life of the data. This is a deliberate design choice, not a defect. The single rule it implies is: **event classes must never be renamed or moved to a different simple name once events of that type exist.**

The risk is purely operational: nothing currently *prevents* a well-meaning rename/refactor (or IDE "rename symbol") from silently changing a persisted type name, with no compile error. So this is a guardrail gap, not an API gap.
- **Recommendation:** state the rule prominently as a hard convention ("never rename an event class — the simple name is a persistence contract"), and add a mechanical guard so accidental renames fail CI rather than corrupting matching — e.g. a committed snapshot/registry of known event-type names checked in the build, or an ArchUnit/test assertion over the event hierarchy. Do **not** add an explicit-name escape hatch; that would dilute the intended one-name-per-class contract.

### P2 — Null-contract coverage is thin
`@NullMarked` appears in only **6 of 45** main `package-info.java` (~13%). jspecify is adopted on the core types but the package-level null contract is the exception, not the rule — across most of the public surface nullability is unspecified.
- **Recommendation:** make `@NullMarked` the default on every published package and treat gaps as bugs.

### P2 — The repo violates its own "no inline FQN" convention
**15** inline fully-qualified references in framework `src/main` (e.g., `new java.util.ArrayList<>()` in `Tag.of`, `java.util.stream.Collectors.toSet()` in `CommandDecision.CommutativeGuarded`). The convention is real and stated in `CLAUDE.md`/the maintainer skill but isn't linter-enforced, so it drifts.
- **Recommendation:** add a Checkstyle/PMD rule (or error-prone) so the convention is mechanically enforced rather than reviewer-dependent.

### P3 — A couple of value-type sharp edges
- `Tag(@Nullable String key, @Nullable String value)` allows a null key on a core identity type; the key is silently lowercased. A null-keyed tag is meaningless but constructible. Consider rejecting null/blank keys.
- `StreamPosition` uses the magic string `"0"` as the default transaction id in `of(...)`/`zero()`, and `transactionId` is stringly-typed (it's an `xid8`). Documented, but a thin wrapper type would be safer than `String`.

### P3 — Build friction from out-of-reactor modules
- `crablet-test-support`, `crablet-test-commands`, `shared-examples-domain`, the examples, and `crablet-codegen` live **outside** the Maven reactor, so a plain `mvn` build can resolve stale SNAPSHOTs; contributors must use `make`. This is documented thoroughly, but it's real onboarding friction, and the active `refactor/test-support-split` work means the test-support layout is still settling.

---

## Bottom line

This reads like a framework built by someone who has done event sourcing before and cares about correctness and developer experience: the DCB strategies are first-class in both the store and the command layer, the public surface is small and labeled, and the docs/tests back it up. The design would not embarrass itself next to mature peers.

What stands between "good code" and "adoptable dependency" is almost entirely **release engineering** (publish artifacts + a BOM, document the Java 25 support stance). The `EventType` behavior is by design — it just needs its "never rename an event class" contract made explicit and CI-guarded. Close P0 (publish + BOM), guard P1 (event-class-name contract), tidy P2 (uniform `@NullMarked`, enforce no-FQN via the checkstyle that's already wired in), and this is something an early-adopter team could responsibly build on.
