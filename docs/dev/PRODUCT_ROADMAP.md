# Crablet Product Roadmap

Last updated: 2026-05-31

---

## Product Vision

Crablet is a Java event-sourcing framework for Spring Boot that makes DCB-style consistency
boundaries the default, not the exception. Its long-term aim is to be the definitive path for
Java developers who want reliable, event-sourced systems without managing the underlying complexity
of event store design, consistency boundary reasoning, or production operations.

The product has three layers that must mature together:

1. **Framework** — the runtime core: event store, commands, views, automations, outbox, poller
2. **AI Workflow** — the modeling, visualization, and generation loop
3. **Ops** — the production deployment story: K8s, observability, scaling, multi-instance

The AI Workflow layer is the primary differentiator and the most novel part of the product.
A framework without it is just another library. With it, Crablet becomes the fastest path from
a domain idea to a running, tested, event-sourced implementation.

---

## The AI-First Workflow: Core Philosophy

### The Three-Step Model

Every feature implemented with Crablet should go through three distinct steps:

```
[1] AI conversation  →  event-model.yaml
[2] Live diagram     ←→  event-model.yaml
[3] Deterministic codegen  →  scaffold  →  user implements
```

These steps are not a one-way pipeline. Steps 1 and 2 form a tight feedback loop — the user
and AI iterate on the model while the diagram provides instant visual confirmation. Only when
the model looks right does the user move to step 3. Step 3 produces deterministic output and
is repeatable: regenerate at any time, always get the same files from the same YAML.

### Step 1 — AI Conversation to event-model.yaml

The AI's primary role is to help the user think about their domain and express that thinking
as a valid `event-model.yaml`. This is not a one-shot generation — it is a conversation.

The conversation has a natural shape:
- User describes what the feature should do in plain language
- AI identifies the commands (what users or systems initiate), events (facts that result), and
  views (what the system must show afterward)
- AI drafts a minimal `event-model.yaml` covering one vertical slice
- User reviews the model and asks questions: "What happens if the account doesn't exist?"
  "Should this be idempotent?" "Who triggers this automation?"
- AI refines the YAML based on the answers

The AI must understand DCB consistency patterns (idempotent vs commutative vs non-commutative
commands, guard events, decision model scope) to make good modeling suggestions. This is not
generic LLM code generation — it requires domain-specific reasoning about the event store's
consistency guarantees.

The `crablet-event-modeling` skill encodes this reasoning. It guides the AI through the
modeling decisions systematically, ensuring the output YAML is correct-by-construction for the
Crablet framework, not just syntactically valid.

### Step 2 — Live Diagram as the Feedback Mechanism

The diagram is not a deliverable — it is the feedback mechanism that keeps the conversation
grounded. Without it, the user has to mentally parse YAML to understand what they've modeled,
which is the wrong abstraction.

The target behavior: any edit to `event-model.yaml` triggers a diagram update within one
second. The user sees the event model board (actors, commands, events, views, automations) in
their browser while the conversation is still live. If the model looks wrong visually, they
catch it immediately rather than discovering it after generation.

This is the tool gap that currently makes the workflow feel disconnected. Closing it turns the
AI conversation from a document-editing exercise into a collaborative modeling session with
real-time visual feedback.

### Step 3 — Deterministic Codegen to Scaffold

Once the model is agreed upon, `crablet generate` produces the scaffold. After H2, this
requires no LLM API key. Same YAML plus same generator version always produces the same files.

The scaffold is not the implementation — it is the contract. The AI's job ends at the YAML.
The generated code (event records, command records, handler interfaces, view projector
skeleton) defines what the user must implement. The user writes the business logic in
user-owned files that are never overwritten by the generator.

This boundary — AI for modeling, deterministic tools for generation, user for business logic —
is the core design principle. It makes the workflow trustworthy: the user knows exactly what
the AI changed (the YAML), exactly what the generator produced (the scaffold), and exactly
what they are responsible for (the handler implementations).

### Why Not Workshop-First?

The canonical Event Modeling workshop (Big Picture → swimlanes → full board) is the right
methodology for team discovery sessions. It is not the right default entry point for a
developer using Claude Code.

A full workshop front-loads modeling decisions before any code feedback. The developer models
an entire bounded context — all commands, all views, all automations, all scenarios — before
generating anything. The risk is that the model doesn't match implementation reality, and
discovering that after a full workshop session is expensive.

Vertical slice-first inverts this: model one command, its events, its view, generate the
scaffold, implement it, and verify it compiles and passes tests. Then extend to the next
slice. Each iteration teaches the user more about the domain. The model grows incrementally
and stays grounded in working code.

The Event Modeling workshop discipline remains valuable for teams doing collaborative
discovery across bounded contexts. It becomes an optional deeper mode for teams, not the
default entry point for a developer building incrementally.

### The Shift: AI Generates Models, Not Code

The most important philosophical change in the Crablet AI workflow is this: **the AI writes
`event-model.yaml`, not Java.**

Currently, the LLM agents generate Java code. This is non-deterministic, requires an API key,
cannot be verified in CI without a live model call, and produces output that varies across
providers and model versions.

After H2, the LLM writes the model file. Deterministic tools write the Java. This separation:
- Makes generation reproducible and testable
- Removes the API key requirement from the generation step
- Makes the model file the auditable artifact (what did the AI change? look at the YAML diff)
- Lets CI verify generated code without any LLM dependency
- Keeps business logic in user-owned files that the AI never touches

The AI remains optional in subsequent steps: `crablet explain` helps the user understand what
was generated, `crablet suggest` proposes model changes when tests fail. Both are explicit,
opt-in, and never part of the default CI path.

---

## Target Users

**Primary: Java backend developers building event-sourced systems on Spring Boot.**
They understand Spring, know PostgreSQL, and want a principled consistency model without
adopting a full CQRS/ES platform. They may work alone or in a small team. They want their
toolchain to help them think through their domain, not just scaffold boilerplate.

**Secondary: Teams doing collaborative domain modeling.**
They run Event Modeling workshops across bounded contexts, and want the model to remain the
authoritative source of truth as implementation evolves. They want CI to enforce that generated
code matches the model, and they want the diagram to be a living artifact, not a snapshot from
an early workshop.

**Out of scope for initial targeting:** enterprise teams requiring SOC2 audit trails,
multi-cloud brokers, or custom compliance frameworks. Those are extension-point users.

---

## Product Pillars

### Framework
The runtime core. Event store, DCB append patterns, command handlers, view projectors,
automation handlers, outbox publishers, shared poller, leader election. The framework is the
foundation. It must be stable before the tooling layer can be trusted.

### AI Workflow
The modeling and generation loop. Three components:

1. **Event modeling conversation** — the AI-guided process of producing `event-model.yaml`
2. **Live diagram** — instant visual feedback as the model evolves
3. **Deterministic codegen** — reproducible scaffold generation from the YAML contract

These three components must work together as a coherent loop, not as independent tools.

### Ops
The production deployment story. Kubernetes manifests, KEDA scale-to-zero, observability,
correlation/causation tracing, multi-instance coordination. Ops maturity is what makes
Crablet adoptable for production workloads, not just prototypes.

---

## Current State (May 2026)

**Framework:** Pre-1.0 API hardening is complete. DCB append patterns, command handler
interfaces, view projectors, automation handlers, and outbox publishers are functionally stable.
Remaining 1.0 work is publication and policy.

**AI Workflow:** Partially implemented. The `crablet-event-modeling` skill guides modeling
conversations. LLM-backed codegen works but is non-deterministic. The diagram renderer exists
as a static HTML tool with no live update mechanism. The three-step workflow exists in concept
but not as a coherent product experience.

**Codegen:** `ArtifactPlanner` enumerates artifacts deterministically. `K8sGenerator` and
`ScenarioScaffoldGenerator` are already deterministic. The plan to replace LLM agents with
deterministic generators is written and sequenced. State derivation metadata does not yet exist
in `event-model.yaml`.

**Ops:** LISTEN/NOTIFY wakeup with pooler detection is implemented. K8s generation exists.
KEDA integration exists. Correlation/causation propagation and observability are post-1.0.

---

## Health Assessment (2026-05-31)

A structural and process assessment (architecture, test ratios, docs, CI, code hygiene) — not a
line-by-line correctness audit of poller/DCB internals.

**Verdict: strong on design, implementation, and documentation; two soft spots — codegen maturity
and adoption surface — both already sequenced in the roadmap below.**

**Strengths (evidence):**

- **Design.** Acyclic module DAG (`eventstore` root; `observability`/`db-migrations` are leaf
  contracts). DCB multi-entity consistency without aggregate-per-command is a genuine differentiator
  in the Java space. Conventions are governed as *closed decisions* (transaction_id linkage,
  ClockProvider, EventType.type, snake_case tags), not informal style.
- **Implementation.** ~21k lines of main Java with **0 TODO/FIXME/HACK** markers and 1 `@Deprecated`.
  ~205 test files; strong ratios where it matters (commands 45, eventstore 37, outbox 32, poller 27),
  Testcontainers-backed.
- **CI.** Beyond build: verifies a committed codegen snapshot, runs doc guardrails, compiles tutorial
  fixtures, regenerates snippets. This "docs can't drift from code" discipline is what makes the
  heavy documentation trustworthy.
- **Documentation.** 84 doc files + ~3,600 lines of module READMEs + skills + concept map; README
  tiers maturity honestly (runtime near-complete, AI tooling in progress, K8s early).

**Soft spots (risk → where addressed):**

1. **Codegen maturity.** It is the headline/differentiator but the least-proven module (thin tests;
   agents→generators refactor in progress). Risk: the marketed track is softer than the runtime.
   → Mitigated by **H2 §2.3 deterministic codegen** (makes generation testable offline in CI) and a
   near-term push on codegen test coverage.
2. **Adoption surface.** Java 25 + Spring Boot 4 is bleeding-edge, narrowing near-term adopters;
   single primary author. → Mitigated by **H1 §1.1 Maven Central publication** and the stated
   Java 25 / Spring Boot 4 / PostgreSQL 17+ baseline + semantic-versioning policy in §1.2.

**Positioning note:** lead the value proposition with the **runtime** (the solid part); present the
AI-first codegen as the in-progress accelerator, consistent with the README's maturity tiering.

---

## Roadmap

### Horizon 1: 1.0 Release (Q2–Q3 2026)

Goal: ship a stable, published framework. No new features. Close the publication gap.

#### 1.1 Maven Central Publication

The single hardest blocker for external adoption. The codegen tool and starter template both
assume Central availability.

- Register at central.sonatype.com and claim `com.crablet` namespace
- Generate GPG key pair for artifact signing
- Add `central-publishing-maven-plugin` and `maven-gpg-plugin` to root POM
- Wire credentials as CI secrets
- Publish snapshot to Central; validate `crablet-app` template resolves it
- Tag 1.0.0 once snapshot validates

#### 1.2 Release Checklist

- `@Stable` / `@Internal` annotations applied (done)
- `UPGRADE.md` complete (done)
- Semantic versioning policy: PATCH = bug fix only, MINOR = new `@Stable` surface,
  MAJOR = breaking change to any `@Stable` type
- Java 25 / Spring Boot 4 / PostgreSQL 17+ baseline stated in README and release notes
- SBOM generated via `cyclonedx-maven-plugin` attached to release

#### 1.3 Fast Handler-Test Base (`crablet-test-commands`)

Done: extracted the command-handler BDD base into a dedicated `crablet-test-commands` module
(`AbstractInMemoryHandlerTest`, in-memory event store, no Postgres) so handler unit tests are fast
and don't depend on the framework's internal `crablet-commands` test-jar. The originally-planned
full split of `crablet-test-support` into `-inmemory` / `-postgres` (with migration relocation) was
**dropped** — it bought only dependency hygiene, not speed, at high build-system churn. The shared
PostgreSQL integration base was renamed separately to `AbstractPostgresEventStoreTest`. An optional
minimal `crablet-test-inmemory` extraction remains documented for later if Testcontainers-on-classpath
becomes a concern.
Record + optional follow-ups: `docs/dev/plans/test-support-fast-slow-split.md`.

#### 1.4 Course Example Committed

The `course-example-app` is implemented but not committed. It is the only example that
demonstrates the multi-aggregate DCB pattern (course capacity + student enrollment limit in
a single consistency boundary). Commit it before 1.0 to make it a regression surface.

---

### Horizon 2: AI Workflow Maturity (Q3–Q4 2026)

Goal: make the three-step workflow (conversation → diagram → codegen) a coherent, fast,
reliable product experience. This is the most important horizon for product differentiation.

#### 2.1 Live Diagram Feedback

**The problem:** The user edits `event-model.yaml` and has to manually reload the HTML renderer
to see the diagram. This kills the modeling loop. The user is forced to mentally parse YAML
instead of reading a board.

**The target:** Any write to `event-model.yaml` updates the diagram within one second.
The user opens the diagram once at the start of a session; it stays current automatically.

**Primary mechanism: `crablet watch`.**

A lightweight watcher process that monitors `event-model.yaml` and re-renders the diagram
whenever the file changes. The diagram HTML is written to `.crablet/preview/index.html` and
served on a local port. The user's browser auto-refreshes via a simple WebSocket or
long-polling endpoint.

```bash
crablet watch          # starts watcher + local preview server on http://localhost:6173
```

The renderer already exists (`docs/event-model-renderer.js`). The watcher wraps it with
file-change detection and a dev server. This is a small amount of infrastructure code with
high workflow impact.

**Secondary mechanism: MCP `crablet_diagram` tool.**

Claude Code (or any MCP client) can call `crablet_diagram` with the current YAML content and
receive a rendered preview. This lets the AI show the user a diagram snapshot inline in the
conversation, without requiring the watcher to be running.

```text
AI writes event-model.yaml
→ AI calls crablet_diagram(yaml_content)
→ Returns rendered HTML path or ASCII board summary
→ User sees the model without leaving the conversation
```

Both mechanisms should coexist. The watcher is the primary developer experience; the MCP tool
is the AI-conversation companion for sessions where the user is not running the watcher.

**What the diagram must show:**

The live diagram must render the canonical Event Modeling board: actors (swim-lane headers),
commands (blue), events (orange), views/read models (green), automations (purple), time
flowing left to right. The board must be legible at the vertical-slice level — one column of
the board per command-event-view triplet. As the model grows, the board grows rightward.

**Why this is the highest-priority H2 item:**

The live diagram is what transforms the AI conversation from a YAML-editing session into a
collaborative modeling session. Without it, the user cannot validate what the AI modeled
without pausing the conversation to manually reload the renderer. With it, modeling mistakes
are caught visually in seconds, before generating any code.

#### 2.2 Vertical Slice-First Onboarding and Workflow

The current `crablet-greenfield` skill and AI workflow docs lead with the full Event Modeling
workshop. This front-loads too much before the user sees any code.

**Revised default entry point:**

```
User: "I need to handle loan applications. An applicant submits an application,
       we record it, and a credit officer can approve or reject it."

AI:   Identifies: 2 commands (SubmitLoanApplication, ApproveApplication),
                  2 events (LoanApplicationSubmitted, LoanApplicationApproved),
                  1 view (LoanApplicationView — status, applicant, timestamps),
                  1 automation (notify credit officer on submission)

AI:   Writes event-model.yaml for this slice only.
      Diagram updates in browser.

User: "The view also needs to show the rejection reason."

AI:   Adds LoanApplicationRejected event, ApproveApplication acquires a rejectReason field.
      Diagram updates.

User: "Looks right. Generate."

crablet generate
```

This is the target conversation shape. The user describes what they want. The AI models it.
The diagram confirms it. Code is generated only when the model looks right.

**Deliverables for H2:**

- Update `crablet-greenfield` to lead with the vertical slice entry point, not the workshop
- Add a "describe your feature" opening to `crablet-event-modeling` skill that produces a
  minimal single-slice YAML before asking about edge cases and automations
- Document the "extend per slice" pattern: how to add the next feature to an existing YAML
  and regenerate safely
- Update AI workflow docs (`docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`) to describe the
  three-step model explicitly
- Define what "done modeling" looks like before moving to generate: diagram looks correct,
  scenarios are present, command patterns are chosen

#### 2.3 Deterministic Code Generation

The detailed plan is in `docs/dev/plans/deterministic-codegen-from-event-model.md`. Summary
of the phases:

**Phase 1 — Contract Freeze (Q3 2026):**
Record the five architectural decisions before any generator code is written:
- Current user-facing packages with generated headers (not `.generated` sub-packages)
- Generated files committed to app repos by default; CI verifies freshness via manifest hash
- Timestamp-based migration names in UTC (`VyyyyMMddHHmmss`), numeric suffix on collision
- Stale file deletion requires `--prune`; `generate` warns but does not delete automatically
- Interim decision: `StateProjector` is user-owned (create-only) until state derivation
  metadata exists in `event-model.yaml`

**Phase 2 — Events and Commands (Q3 2026):**
Implement `EventsGenerator` (sealed interface + event records) and `CommandsGenerator`
(command records + handler interface contracts). These are purely structural, have no design
ambiguity, and immediately reduce LLM dependency for the most common generation step.
Acceptance: events, command records, and handler contracts for the loan model generate without
any LLM API key.

**Phase 3 — Views (Q3–Q4 2026):**
Implement `ViewsGenerator` (view projector + timestamp-versioned SQL migration). Migration
drift detection is out of scope for this phase — planned separately.

**Phase 4 — Automations and Outbox (Q4 2026):**
Implement `AutomationsGenerator` and `OutboxGenerator`. Both generate metadata-only interfaces
(three and two default methods respectively) with all values coming directly from the YAML.
Lowest-risk generators in the plan.

**Phase 5 — Remove LLM from Default Generate (Q4 2026):**
Make `crablet generate` fully deterministic. Rename the current LLM path to `crablet repair`
(optional, explicit, manifest-aware). No LLM API key required for `plan`, `generate`, `k8s`,
or `sync-scenarios`. CI runs codegen snapshot verification offline.

#### 2.4 State Derivation Metadata in event-model.yaml

Deterministic `StateProjector` generation is blocked because the generator cannot infer which
state fields to emit from event presence alone. Unblocking it requires adding explicit state
derivation metadata to the event model YAML.

This is a YAML schema design problem, not a generator implementation problem. The design must
be validated against all three example domains (loan, wallet, course) before committing to a
format.

**Options under consideration:**

Option A — `state:` section listing fields and derivation rules:
```yaml
state:
  fields:
    - name: isExisting
      type: boolean
      setOnEvent: LoanApplicationSubmitted
    - name: isApproved
      type: boolean
      setOnEvent: LoanApplicationApproved
    - name: creditScore
      type: int
      fromField: LoanApplicationApproved.creditScore
```

Option B — per-event `contributes:` metadata:
```yaml
events:
  - name: LoanApplicationSubmitted
    contributes:
      isExisting: true
  - name: LoanApplicationApproved
    contributes:
      isApproved: true
      creditScore: event.creditScore
```

Option A is more explicit and self-documenting. Option B keeps state derivation collocated
with events, which may read more naturally for modeling conversations.

Target: decide the format in Q3 2026, implement it in Q4 2026. This unblocks deterministic
`StateProjector` generation as a Phase 3 extension.

#### 2.5 The Conversation Design Problem

Getting the AI conversation quality right for step 1 is the hardest non-technical challenge
in the workflow. The conversation must produce a valid, idiomatic `event-model.yaml` — not
just a syntactically correct YAML, but one where:

- Command patterns (idempotent, commutative, non-commutative) are correctly assigned
- Guard events are used where lifecycle preconditions exist
- Tags are chosen as DCB consistency keys, not just metadata
- Views are modeled from the query side ("what does the UI need to show?"), not from events
- Automations are identified as reactions ("when X happens, do Y"), not as side effects

This requires the AI to reason about DCB consistency semantics during the conversation, not
just transcribe what the user says into YAML. The `crablet-event-modeling` and `crablet-dcb`
skills encode these rules. They must stay current with framework API changes.

**The repair signal:** if the user frequently needs to correct the AI's pattern choices after
generation, the skill prompt is missing context. The primary fix is always to improve the
skill/prompt, not to improve the repair loop. A better model at step 1 eliminates repair
at step 3.

**Testing the conversation quality:** add snapshot tests that verify the AI produces a correct
event-model.yaml for well-defined domain descriptions. These are prompt-level regression tests,
not code tests. They run against the actual LLM and are not part of normal CI — they run on
demand when the skill is updated.

#### 2.6 crablet explain and crablet suggest

After deterministic generation is the default, AI adds value in two explicit modes:

**`crablet explain`:**
Takes the current `event-model.yaml` and `plan` output and explains in plain language what
will be generated and why each artifact exists. Useful for users learning the framework or
reviewing AI-authored models before committing.

**`crablet suggest`:**
Takes compile errors or test failures and proposes changes to `event-model.yaml` or
user-owned files. Never modifies generated files directly. Returns a proposed diff for the
user to review and accept. This replaces the current prompt-only repair loop with an
explicit, reviewable suggestion.

Neither command is part of the default `generate` path. Both require an LLM call, are opt-in,
and are manifest-aware (they know which files are machine-owned and do not propose edits to
them).

#### 2.7 Repair Loop and Manifest Consistency

The existing fence-stripping and repair loop in `RepairAgent` is defensive tooling. It must
not be extended — the investment goes into deterministic generators, not better prompts.

`crablet repair` must either update the manifest when it writes machine-owned files, or be
explicitly documented as manifest-unaware. Silent writes to machine-owned files without
manifest updates create drift that `generate` cannot detect and the user cannot explain.

---

### Horizon 3: Operations and Production Readiness (Q4 2026 – Q1 2027)

Goal: make Crablet trustworthy in production. Observability, multi-instance coordination,
correlation tracing, performance baselines.

#### 3.1 Correlation and Causation End-to-End

`crablet-commands-web` captures incoming headers and sets `CorrelationContext`. Remaining:
- **Views:** no correlation context binding in the view dispatch path; add binding so view
  projectors can forward correlation IDs to external calls
- **Outbox:** `publishBatch` receives `StoredEvent` with `correlationId`/`causationId`;
  document envelope conventions for HTTP and Kafka payloads
- **Integration test:** verify IDs survive command → automation → view → outbox using the
  Course example

#### 3.2 Observability

**Metrics:** extend `crablet-metrics-micrometer` with:
- Command execution latency histogram by command type
- Event append latency histogram
- Poller lag gauge (latest event position vs processed position per poller)
- Outbox publish success/failure rate

**Health indicators:** Spring Boot health endpoint integration for event store connectivity,
poller health (last successful poll timestamp), and leader election status.

**Structured logging:** consistent log field schema for event append, command execution,
poller dispatch, and automation decisions. Correlation IDs appear in log lines automatically
when `CorrelationContext` is set.

#### 3.3 Performance Baselines and Tuning Guide

Document expected performance characteristics and provide a tuning guide covering:
- Append throughput under single-writer and multi-writer workloads
- View projector lag under high event rates
- Poller wake latency with LISTEN/NOTIFY vs scheduled polling
- Connection pool sizing for read and write data sources
- Outbox publisher concurrency

#### 3.4 LISTEN/NOTIFY Ergonomics

- Auto-detect whether the configured data source supports `pg_notify` before starting the
  wakeup source; fall back gracefully with a clear log
- Document PgBouncer session mode configuration for LISTEN/NOTIFY compatibility
- Test coverage for the pooler fallback path

#### 3.5 K8s and KEDA Production Guide

`K8sGenerator` already produces Kubernetes manifests. Fill in the production gaps:
- Liveness and readiness probe configuration
- KEDA ScaledObject for outbox workers with scale-to-zero
- LISTEN/NOTIFY wakeup interaction with KEDA (sleeping pod wakes on pg_notify)
- Resource request/limit recommendations
- Rolling update strategy that avoids split-brain on advisory lock holders

---

### Horizon 4: Ecosystem Maturity (2027)

Goal: extensibility, community readiness, complex domain scenarios.

#### 4.1 ECS/Fargate Deployment (MiniStack Spike)

Validate the Crablet deployment model on ECS/Fargate with a MiniStack emulator before
committing to Terraform/CDK. Test LISTEN/NOTIFY over direct RDS connections, task-level
leader election, and ECS service auto-scaling as a KEDA alternative.

#### 4.2 Shared Event Types and $ref in event-model.yaml

Add `$ref` support to reference shared event types from a sibling YAML or central schema
registry. This enables cross-bounded-context event sharing. Deferred until single-context
generation is solid and trustworthy.

#### 4.3 Migration Drift Detection

Detect when generated migrations diverge from the current model: added/removed/renamed
columns. Generate additive migrations automatically; fail with a clear message on destructive
changes. Planned but explicitly out of scope until H4.

#### 4.4 VS Code Extension (Nice-to-Have)

If the user base includes developers in non-AI flows who want YAML schema validation and
diagram preview without an AI frontend, a VS Code extension is worth building. Low priority
while MCP covers the primary user base.

#### 4.5 Enterprise Extension Points

Gherkin/BDD and EventCatalog integration are intentionally out of scope for the Crablet core.
At H4, document the extension surface — where to hook scenario executors, how to publish the
event model to an external catalog — without implementing it in the core.

---

## Target Workflow: End-State Vision

The complete developer session at the end of H2:

```
# Bootstrap
crablet-greenfield init my-loan-app
cd my-loan-app
crablet watch                    # starts diagram preview at localhost:6173

# Session: model and implement first slice
# Claude Code conversation:

User: "I need to handle loan applications. An applicant submits a loan application.
       It needs to be reviewed by a credit officer, who can approve or reject it
       with a reason."

AI:   "Here's what I'm modeling:
       - Commands: SubmitLoanApplication (idempotent — creates the entity),
                   ApproveLoanApplication (non-commutative — order matters),
                   RejectLoanApplication (non-commutative)
       - Events:   LoanApplicationSubmitted, LoanApplicationApproved,
                   LoanApplicationRejected
       - View:     LoanApplicationView (applicationId, status, applicantId,
                   creditScore?, rejectionReason?, timestamps)
       - Automation: notify credit officer on submission
       Writing event-model.yaml..."

# AI writes event-model.yaml
# Diagram updates in browser immediately
# User reviews the board

User: "The credit officer needs to see all open applications. Add a list view."

AI:   "Adding OpenLoanApplicationsView (applicationId, applicantId, submittedAt)
       — only includes applications in PENDING status. Updating YAML..."

# Diagram updates, new green card appears on the board

User: "Looks right."

crablet plan
# Output shows: 3 event records, 3 command records, 3 handler interfaces,
#               LoanApplicationStateProjector (user-owned scaffold),
#               LoanApplicationViewProjector, OpenLoanApplicationsViewProjector,
#               2 migrations, 1 automation interface, scenario scaffolds

crablet generate                 # no LLM API key needed
# All files written to src/main/java and src/test/java

# User implements:
vim src/main/java/.../command/SubmitLoanApplicationHandler.java
vim src/main/java/.../command/ApproveLoanApplicationHandler.java
vim src/main/java/.../command/RejectLoanApplicationHandler.java
# StateProjector already scaffolded (user-owned), customize as needed

./mvnw verify                    # all scenario tests pass

# Next session: add a second slice
User: "Now I need to handle credit scoring. When an application is submitted,
       we call an external scoring service and record the score."

# AI extends event-model.yaml with new automation and outbox publisher
# Diagram adds new swimlane
# crablet generate adds new files, existing files untouched
# User implements the outbox publisher for the external scoring call
```

This workflow — AI models, deterministic tools generate, user implements business logic —
should be achievable in under 30 minutes for a one-slice feature from blank slate to green tests.

---

## What We Are Not Building

**LLM in the generate path.** After H2, the AI writes `event-model.yaml`, not Java.
Generation is deterministic. This is the central architectural decision of the product.

**Gherkin / BDD as first-class.** Scenario scaffolds follow a JUnit structure. Full Gherkin
parsing, Cucumber integration, and step definition generation are enterprise extension points.

**EventCatalog integration.** The `event-model.yaml` file is the catalog. External publishing
is an enterprise extension.

**Multi-language support.** Crablet is Java-first. No other languages are on the roadmap.

**Managed cloud service.** Crablet is a library and toolchain. Users run it on their own
infrastructure.

**Event store clustering or multi-region.** PostgreSQL handles this. Crablet does not add
its own replication layer.

---

## Strategic Open Questions

**State derivation metadata format.** `state:` section vs per-event `contributes:` vs something
else. Must be validated against the loan, wallet, and course domains before committing.

**Live diagram mechanism.** `crablet watch` (file watcher) vs MCP `crablet_diagram` vs both.
Both are implementable; the file watcher ships first since it has no AI dependency.

**Conversation quality testing.** How to detect prompt drift in `crablet-event-modeling` before
it causes wrong models in production use. Snapshot tests against a live LLM are expensive.
A golden-file approach (committed YAML outputs for standard domain descriptions, compared on
skill update) is more tractable.

**$ref scope.** Before cross-context event sharing: does the shared schema live in a sibling
YAML, a URL, or a central registry? Wrong choice creates a migration headache at H4.

**CI verification of generated files.** "Fresh" means: hash of `event-model.yaml` + generator
version matches the manifest. Define the error message and developer action clearly so CI
failures are actionable ("run `crablet generate` and commit the result").
