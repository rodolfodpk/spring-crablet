# Assessment: Deterministic Codegen From Event Model

Assessed against: `deterministic-codegen-from-event-model.md`
Date: 2026-05-25

---

## Executive Summary

The plan is architecturally sound and the direction is correct. The core change — moving Java
generation from LLM agents to deterministic generators driven by `event-model.yaml` — removes the
main reliability, testability, and accessibility problems with the current system. The phasing is
logical. Several design decisions need to be settled before Phase 2 begins, and one structural
tension (the `StateProjector` extension point) needs a concrete answer before it becomes a
user-facing contract.

---

## The Core Architectural Change

The plan replaces this flow:

```
event-model.yaml → LLM prompt → LLM call → parse response → strip fences → write files
```

with:

```
event-model.yaml → Generator → write files
```

This is the right change. The current `*Agent` classes carry all the generation knowledge in their
prompt strings, which means:

- generation results are non-deterministic across runs, providers, and model versions
- tests can only snapshot one particular LLM output, not the generation logic itself
- any user without an API key cannot run `generate`
- prompt drift silently changes generated code

The `ArtifactPlanner` is the strongest existing asset for this transition: it already enumerates
every artifact by name, package, and kind. Deterministic generators just need to produce the file
content for each `PlannedArtifact`. The naming and structural rules that currently live in LLM
prompts move into generator code where they can be unit-tested directly.

---

## Phase Analysis

### Phase 1: Freeze The Contract

**Status: largely already done.**

The machine-owned / user-owned split is documented in `crablet-codegen/CLAUDE.md`. The
`ScenarioScaffoldGenerator` already implements create-only behavior. The main gap is:

- no `make codegen-snapshot-verify` target
- no test asserting scenario scaffold files are never overwritten
- no generated-file headers in current machine-owned output

This phase should be treated as a one-sprint housekeeping pass, not a substantial design phase.
The acceptance criteria are achievable quickly and should be done before any Phase 2 work to
prevent generated-file ownership ambiguity during the transition.

### Phase 2: Deterministic Events And Commands

**This is where the transition actually happens and where the design decisions bite.**

Events are the easiest starting point: every `EventSpec` maps directly to a record with typed
fields. The sealed interface root is a function of the domain name. There is no logic to generate —
only structure. An `EventsGenerator` that does not call an LLM is a one-day implementation.

Command records are similarly structural: each `CommandSpec` maps to a record with typed fields and
YAVI constraints. Mechanical.

Command handler interfaces are structural: the append strategy in the YAML determines which
`*CommandHandler` sub-interface to extend. No LLM required.

**The hard part is `State`, `StateProjector`, and `QueryPatterns`.** See the dedicated section
below.

The acceptance criterion — "loan snapshot can be generated without any LLM key" — is the right
goal, but it requires `State`, `StateProjector`, and `QueryPatterns` to be fully generated, not
just the structural pieces.

### Phase 3: Deterministic Projectors And Views

View projectors are more tractable than they appear. `AbstractTypedViewProjector` takes care of the
framework wiring; the generator only needs to produce `handleEvent()`. Given that `ViewSpec`
contains the event list and `SchemaSpec` contains the column list, the switch cases and INSERT/UPDATE
SQL are mechanical templates.

Migration generation is tractable but has a critical unsettled design question (versioning scheme —
see Open Questions).

The drift detection requirement ("detect drift between the model and existing migrations, fail with
a clear message on destructive changes") is the most complex acceptance criterion in this phase. It
requires comparing the current `SchemaSpec` against the last generated migration to identify added,
removed, and renamed columns. This is a non-trivial feature; it should be scoped explicitly rather
than listed as an acceptance bullet.

### Phase 4: Deterministic Automations And Outbox

These are the easiest generators in the plan. The current `CLAUDE.md` templates show that
automation interfaces expose three default methods (`getAutomationName`, `getEventTypes`,
`getRequiredTags`) and outbox interfaces expose two (`getName`, `getPreferredMode`). All values come
directly from the YAML. This phase may be implementable in the same sprint as Phase 3 view
projectors.

### Phase 5: Remove LLM From Default Generate

The acceptance criterion "clean template app can run `make generate && make verify` with no LLM API
key" requires a committed answer on whether generated files are checked in. If they are not checked
in, CI must run the generator, which means the generator artifact must be present. If they are
checked in, CI verifies they match a re-run. The plan defers this decision; it must be made in
Phase 1 so the template repo, Makefile, and CI workflow can be written correctly.

---

## Generator Tractability Matrix

| Generator | Difficulty | Reason |
|-----------|-----------|--------|
| `EventsGenerator` | Low | Pure structure: sealed interface + one record per event, fields from YAML |
| `CommandsGenerator` (command records) | Low | Fields + YAVI constraints, fully in the model |
| `CommandsGenerator` (handler interfaces) | Low | Append strategy → sub-interface selection, mechanical |
| `AutomationsGenerator` | Low | Three metadata methods, all values in YAML |
| `OutboxGenerator` | Low | Two metadata methods, all values in YAML |
| `CommandsGenerator` (QueryPatterns) | Medium | DCB criteria from tag keys — values are in the model but method bodies are real code |
| `ViewsGenerator` (projector) | Medium | handleEvent() switch + SQL from SchemaSpec — structural but detailed |
| `ViewsGenerator` (migrations) | Medium | SQL DDL from SchemaSpec — tractable, complicated by versioning and drift detection |
| `CommandsGenerator` (StateProjector) | High | transition() switch over sealed hierarchy + state field updates; see below |
| `CommandsGenerator` (State record) | Medium | Boolean flags and numeric fields must be derivable from event field names in the YAML |

---

## The StateProjector Extension Point Problem

This is the single most important design decision the plan does not resolve.

Currently, `CommandsAgent` generates `StateProjector.transition()` using an LLM that understands
the domain. The generated code contains a switch over sealed event variants and updates state fields
based on event data. After generation, the user can edit this file to add domain logic the LLM
missed.

Under the new plan, `StateProjector` becomes machine-owned and regeneratable. This means:

1. The generator must emit correct transition logic from the YAML alone
2. The user cannot customize `transition()` without losing their edits on regen

For cases where the YAML is fully expressive (simple boolean flags, direct field copies), this is
fine. But `StateProjector` is on the hot path of every command execution, and real domains
frequently need:

- accumulated counts derived from multiple events
- conditional flags that depend on event field values, not just event presence
- multi-event derived state that doesn't map to a single field assignment

**Recommended resolution:** Generate `StateProjector` as a machine-owned class with protected
per-event hooks that the user can override in a user-owned subclass:

```java
// Generated — do not edit. Customize by subclassing and overriding on* methods.
public class LoanStateProjector implements StateProjector<Optional<LoanState>> {

    @Override
    public Optional<LoanState> transition(Optional<LoanState> state, StoredEvent stored,
                                          EventDeserializer d) {
        LoanEvent event = d.deserialize(stored, LoanEvent.class);
        return switch (event) {
            case LoanApplicationSubmitted e -> onLoanApplicationSubmitted(state, e);
            case LoanApplicationApproved e -> onLoanApplicationApproved(state, e);
        };
    }

    protected Optional<LoanState> onLoanApplicationSubmitted(
            Optional<LoanState> state, LoanApplicationSubmitted e) {
        return Optional.of(new LoanState(true, false, e.applicationId()));
    }

    protected Optional<LoanState> onLoanApplicationApproved(
            Optional<LoanState> state, LoanApplicationApproved e) {
        return state.map(s -> s.withApproved(true));
    }
}
```

User-owned customization lives in a separate file:

```java
// User-owned
@Component
class LoanStateProjectorCustom extends LoanStateProjector {

    @Override
    protected Optional<LoanState> onLoanApplicationApproved(
            Optional<LoanState> state, LoanApplicationApproved e) {
        // custom accumulated logic here
        return state.map(s -> s.withApproved(true).withCreditScore(e.creditScore()));
    }
}
```

This pattern:
- keeps the sealed-type switch machine-owned (safe to regenerate when events are added)
- gives users a stable override surface that survives regeneration
- requires no new framework API — it is a plain Java inheritance pattern
- aligns with how `AbstractTypedViewProjector` already works for view projectors

The `QueryPatterns` class has a similar tension but is less severe: the methods are static factories
returning `Query` objects built from tag constants. If the user needs a custom query variant, they
can add it to the user-owned handler implementation rather than to `QueryPatterns` itself.

---

## Open Questions: Priority Order

### Must resolve before Phase 2

**1. Package naming: `.generated` sub-package vs. current package with header.**

This is a user-facing API decision. Changing it after Phase 2 ships breaks every user handler
import. Recommendation: use current user-facing packages with a `// Generated by Crablet` header
and manifest entry. A `.generated` sub-package leaks implementation detail into user code and makes
imports awkward.

**2. StateProjector extension model.**

Must be decided before the `CommandsGenerator` implementation begins. The protected-hook subclass
pattern described above is the recommended answer. The decision needs to be recorded in the plan
and in `crablet-codegen/CLAUDE.md` so future generators follow the same convention.

**3. Should generated files be committed to application repos by default?**

This determines CI architecture for Phase 5. If generated files are committed: CI runs
`make codegen-snapshot-verify` (fast, offline, deterministic). If not committed: CI must run
`make generate` as part of the build (requires the generator artifact, adds a build step). The
template repo and Makefile cannot be written correctly without a decision here.

### Must resolve before Phase 3

**4. Migration versioning: timestamp vs. sequential.**

Sequential numbers (`V100`, `V101`) create merge conflicts when two branches each add a view.
Timestamp-based versions (`V20260525135201__...`) are branch-safe but harder to read and
diff. Recommendation: timestamp-based, matching Flyway's own recommendation for team environments.
This must be decided before the migration generator is implemented; changing it later requires
renaming all generated migrations.

**5. Stale file deletion: automatic vs. `--prune`.**

When a model entry is removed, automatic deletion of the generated file is risky on feature
branches where the entry is temporarily absent. Recommendation: `--prune` flag required for
deletion, with `generate` warning about stale files but not removing them. The manifest enables
the warning even without `--prune`.

### Can be deferred to Phase 5

**6. LLM rename: what does the explicit experimental command look like?**

The current `crablet generate` with provider config becomes `crablet repair` (or similar).
The exact command name and flags can wait until Phase 5, as long as the `RepairAgent` is not
broken during Phases 2–4.

---

## Risks

**RepairAgent and the manifest.** The plan does not address what happens when `crablet repair`
writes files. If `RepairAgent` creates or modifies machine-owned files, the manifest becomes stale
and subsequent `generate` runs may produce unexpected diffs. The repair loop should either update
the manifest on write or be explicitly documented as "manifest-unaware."

**JavaPoet and modern Java.** JavaPoet is recommended for structured Java rendering. Java 25 sealed
interfaces with `permits` clauses, records, and pattern-switching have not always been well-supported
in JavaPoet. Before committing to it in Phase 2, verify it handles
`sealed interface LoanEvent permits LoanApplicationSubmitted, LoanApplicationApproved` correctly.
If it does not, the `TemplateLoader` infrastructure already in the codebase is a safe fallback —
string templates with substitution are less elegant but known-correct.

**State record field derivation.** The plan lists `State` records as machine-owned. Currently the
LLM infers which boolean flags to include from event semantics (e.g., `isExisting`, `isApproved`).
A deterministic generator cannot infer semantics; the flags must come from the YAML. The
`event-model.yaml` schema may need a `state` section or event-level annotations to make this
possible. If `State` field derivation is not feasible from the current YAML schema, `State` should
be treated like `StateProjector` — machine-generated skeleton, user-customizable via subclass or
kept user-owned.

---

## Recommendations

1. **Settle the three Phase-2-blocking decisions first** (package naming, StateProjector extension
   model, committed-vs-generated CI model) before writing any generator code. Record the decisions
   in the plan document and `crablet-codegen/CLAUDE.md`.

2. **Implement `EventsGenerator` first** as a proof of concept. It is the simplest generator, has
   zero design ambiguity, and validates the generator infrastructure (file writing, manifest update,
   header emission) before the harder cases.

3. **Validate JavaPoet against sealed interfaces and records** in a spike before adopting it.
   Keep `TemplateLoader` as the fallback.

4. **Scope migration drift detection separately.** It is the most complex acceptance criterion in
   Phase 3 and should have its own plan entry rather than being buried in a bullet point.

5. **Add a `state` section or event-level `contributes` annotations to `event-model.yaml`** to make
   `State` record field generation deterministic. Without this, the generator cannot know which
   flags to emit and must fall back to a skeleton with placeholder fields.

6. **Do not invest further in prompt-only fixes** while this plan is in progress. The fence-stripping
   and repair loop should be kept working but not extended. New LLM prompt work in the agents
   during this period is waste.
