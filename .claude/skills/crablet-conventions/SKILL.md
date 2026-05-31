---
name: crablet-conventions
description: >
  Use this skill when the user wants to:
    - Review a diff or file against spring-crablet's repo conventions and closed design decisions
    - Check Crablet-specific rules generic linters/code-review miss (ClockProvider, EventType.type,
      no-FQN, snake_case tags, the transaction_id linkage invariant, audit storeCommand placement)
    - Pre-flight a change before committing or opening a PR in this repo
    - Understand WHY a convention exists, not just that it was violated
argument-hint: [optional: file, module, or "diff" to review]
---

<!-- crablet-banned-terms: allow — this skill documents the "no stale embabel" rule and must name the term. -->

# Crablet Conventions Review

A focused checklist for spring-crablet's **own** conventions and closed design decisions — the rules
that `/code-review` and generic linters don't know about. Use this in addition to, not instead of,
`/code-review` (bugs) and `/crablet-maintainer` (framework change guidance).

When reviewing, report each finding as: **file:line → rule → why it matters → fix**. Prefer `grep`
over reading whole files for the mechanical rules below.

## Code conventions (CLAUDE.md > Repo Conventions)

1. **No fully-qualified class names inline.** Add an import instead of `com.foo.Bar` in code.
   - Detect: identifiers with `.` package paths used inline in `*.java` (excluding imports/annotations like `@org.jspecify...` where already idiomatic).
2. **Never call `Instant.now()` directly.** Inject `ClockProvider` and call `clockProvider.now()`.
   - Detect: `grep -rn "Instant.now()" --include=*.java src/main`. Time must be injectable for tests.
3. **Use `EventType.type(Class)` for event type names** — not string literals for event types.
   - Detect: event-type strings that duplicate a class name instead of `type(SomeEvent.class)`.
4. **snake_case tag keys.** Keys are normalized to lowercase; values stay case-sensitive.
   - Detect: camelCase or PascalCase tag keys in `.tag(...)` / tag constant classes.
5. **Prefer domain-specific query pattern helpers** for reused decision models rather than inlining
   the same `Query` construction across handlers.

## Closed design decisions (CLAUDE.md > Design Decisions) — do not "fix" these

6. **Command→event linkage is by `transaction_id`, never `command_id`.** Both tables share the same
   `pg_current_xact_id()` when written in one DB transaction.
   - **Flag any proposal** to add a `command_id` column to `crablet_events` or `crablet_event_tags`
     — that decision is closed. This is a frequent "helpful refactor" that must be rejected.
7. **`storeCommand` runs inside `executeInTransaction` on the scoped store.** Audit linkage requires
   `CommandAuditStore.storeCommand` to be called on the transaction-scoped `ConnectionScopedEventStore`,
   never on the top-level `EventStoreImpl`. `CommandExecutorImpl` upholds this; hand-written
   tests/callers must too.

## Build / workflow rules

8. **Never `./mvnw test -pl <module>` without `-am`** — resolves a stale SNAPSHOT sibling and tests
   against old migrations. Use `make test-pl PL=<module>`. (A PreToolUse hook enforces this; see
   `scripts/hooks/`.)
9. **Significant API-breaking changes need approval first** — present before/after examples and wait
   for sign-off before implementing (project working agreement).
10. **No stale `embabel` references** — the module is `crablet-codegen`; Embabel is no longer used.
    Only the superseded plans under `docs/dev/plans/` may mention it. (A PostToolUse hook warns.)

## Docs / diagram rules

11. When changing `docs/event-model-renderer.js` or describing a canonical actor board, align with
    `/crablet-diagram-advisor` and `docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`.
12. Use Event Modeling vocabulary consistently: **rows** are semantic element layers; **lanes** are
    subsystem/bounded-context groupings; time flows left to right.

## Related

- `/code-review` — general correctness/quality review (run this too)
- `/crablet-maintainer` — how to make framework changes safely
- `/crablet-dcb` — the consistency model behind decisions 6–7
- `/crablet-test-authoring` — getting linkage right in tests
