# Plan: First-class support for the todo-list read model + automation pattern

**Status:** Draft — plan iteration 2026-05-02.

**Context:** Event Modeling often shows automations that **read a todo-queue view** before issuing commands or driving outbox. Crablet **automations are event-driven today**: `AutomationHandler` subscribes to **`getEventTypes()`** (and tags); the poller delivers **`StoredEvent`** batches to **`decide(event)`**. A **read model is not** a poller subscription. Application code can already **`decide`** by reading a view, but nothing in the framework **declares** that contract or **coordinates projection readiness**.

This plan captures **tradeoffs** and a **recommended direction** without locking implementation details.

---

## Pattern variants

| Variant | Wake-up mechanism | View role | Config surface |
|--------|-------------------|-----------|----------------|
| **A. Event wake-up only (today)** | Matching domain events | Read inside `decide` (optional) | `getEventTypes()` + tags |
| **B. Event wake-up + declared reads** | Same as A | Same, but **named in model** for docs/codegen/diagrams | A + **`readsViews`** (or equivalent) |
| **C. Event wake-up + projection barrier** | Same as A | Same as B; framework **waits** until listed views have processed **up to causation** before `decide` | B + **`projectionBarrier`** / view list |
| **D. View-primary poller** | Interval / cursor on **view rows** (or queue table) | Source of **when** to run | New processor type, second cursor model |
| **E. Inferred event wake-up** | Same as **B**, but event types are **derived** (union of `view.reads` for declared **`readsViews`**) | Read inside `decide` | **`readsViews` only** (+ optional overrides / exclusions) — **no hand-listed poke events** |

---

## Option D and boilerplate: does it make sense?

**Why D looks attractive:** authors declare **only** “this automation reads **`PendingQueue`**” and do **not** maintain a parallel list of **every event type** that can change that view. That removes duplicated config and avoids “forgot to subscribe to `FooHappened`” bugs.

**Catch:** much of that **ergonomic win** is available **without** variant **D** if the framework (codegen or runtime wiring) implements **E**:

- Given **`readsViews: [V1, V2]`**, compute **`wakeEventTypes = union(V1.reads, V2.reads, …)`** from the **same `event-model.yaml`** and feed that into the existing **`EventSelection`** / handler registration.
- **Overrides** remain for edge cases: extra events that **indirectly** change the view (e.g. via another projection path), or **exclusions** if the union is too coarse.

**E** keeps **one** poller, **event positions** as cursors, and **causation** on each batch — you only reduced **author-written** YAML/Java noise.

**When D still adds real value**

- **Time-based sweeps** with **no** recent domain event (e.g. “retry stale pendings every N minutes”) — event-only wake-up needs either a **scheduled tick event** (awkward) or **D-style** polling.
- **Strong** “run **exactly** when queue non-empty” semantics without **NoOp** passes on irrelevant events — usually **nice-to-have**, not worth D until proven.
- **Heterogeneous** view storage where “what changed” is **not** expressible as stream events the app owns (rare in pure event-sourced Crablet).

**Verdict:** **D makes sense** as a **longer-term optional module** if **E + optional scheduler tick** still feels clumsy — but **try E first**: same boilerplate reduction for the common case, **much** lower framework cost than a view-row cursor and second truth.

---

## Where view `reads` lives today (YAML vs Java)

| Layer | What it knows | Consumed by |
|-------|----------------|-------------|
| **`event-model.yaml`** | Each view has **`reads: [EventName, …]`** ([`ViewSpec`](../../embabel-codegen/src/main/java/com/crablet/codegen/model/ViewSpec.java)) | **Codegen** (e.g. LLM prompt in [`ViewsAgent`](../../embabel-codegen/src/main/java/com/crablet/codegen/agents/ViewsAgent.java)), **diagrams**, **topology** ([`K8sTopology`](../../embabel-codegen/src/main/java/com/crablet/codegen/k8s/K8sTopology.java)) — **not** loaded by `crablet-views` at runtime |
| **Runtime** | [`ViewSubscription`](../../crablet-views/src/main/java/com/crablet/views/ViewSubscription.java) on each **`ViewProjector`** (`getEventTypes()`, tags) | `ViewEventFetcher` → SQL `WHERE` for the **view** `EventProcessor` |

So projectors **in code** already declare the event set the poller uses. That set **should** match **`views[].reads`** after a codegen pass; for hand-written projectors, only **Java** is authoritative unless you re-sync from YAML.

**Implication for variant E:** inferred automation wake events can use **`views[].reads` from the event model** (single declarative source) **or**, in a later phase, **reflect** over registered `ViewProjector` beans to avoid drift — codegen **should** keep them aligned.

---

## Concise reference: options and tradeoffs

**Three decisions matter most:**

1. **What wakes the automation?**  
   - **Events (default):** one poller, one cursor on the **event log**, every run has **causation** — best for audit and replay.  
   - **View rows / schedule (D):** simpler “queue” mental model and **time-only** sweeps — costs a **second** trigger story and weaker ties to stream position.

2. **Who defines the wake event list?**  
   - **By hand in Java:** maximum control, easy to get **out of sync** with what actually updates the todo view.  
   - **Inferred (E):** declare **`readsViews`** only; union of inputs comes from **YAML `reads`** (docs/codegen) or from **`ViewSubscription` in Java** (runtime truth). YAML is convenient for one-file modeling; Java introspection avoids **drift** with projectors.

3. **Stale reads?**  
   Wake event arrives **before** the projector applied it → automation may see an **old** queue. Fix with **idempotent** handlers, or **projection barrier (C)**, which adds **complexity**.

**Short verdict:** Prefer **event wake-up + E** (infer from YAML *or* Java depending on your pipeline); add **C** only if staleness is real; use **D** only if you need **time-driven** or strictly **queue-native** triggering that events alone don’t express cleanly.

**YAML authoring vs Java:** Event (and view `reads`) names in `event-model.yaml` are **strings** — you **lose** the same **refactoring / completion** you get from `EventType.type(MyEvent.class)` in a projector or handler. Mitigations: **JSON Schema** (e.g. [`event-model-schema.json`](../../docs/user/examples/event-model-schema.json)) wired in the IDE for YAML, **codegen** so app code stays typed, **tests** that load the model and resolve names against compiled types, and **convention** (event names match simple class→type rules). Prefer **Java as canonical** for subscriptions in code-first apps; use **YAML as canonical** when the model file drives AI/codegen and accept tooling around the strings.

| Question | Prefer this | Unless… |
|----------|-------------|---------|
| **Wake mechanism** | **Events** (log cursor, causation) | You need **queue/timer-first** triggering → then consider **D** |
| **Inference (E)** | **YAML** `reads` union (Ea) for one-file / codegen | **Runtime** must mirror projectors exactly → **Java** union (Eb) |
| **Ordering** | Idempotent / cheap **NoOp** in `decide` | Stale queue bites → **barrier (C)** |

---

## Tradeoffs

### Keep event subscription as wake-up (A → B → C)

**Pros**

- Reuses **one** poller pipeline: cursors, leader election, shared-fetch, LISTEN/NOTIFY, metrics.
- **Audit story stays in the log**: every automation run ties back to a **causation event** (already propagated in `AutomationDispatcher` via `CorrelationContext`).
- **No second truth**: work is still ultimately derived from events that updated the read model; you do not maintain an independent “work queue cursor” that can drift from the event stream.
- Aligns with **how views are built in Crablet** (view projectors consume **events**, not the reverse).

**Cons / “more config than simply read model”**

- Authors must choose **which event types** should **poke** the automation (often **several**: anything that can enqueue/dequeue work in the todo view). Missing a type ⇒ **stale** queue until another event arrives.
- **`decide` may run when the view has no pending work** ⇒ must return **`NoOp`** cheaply (acceptable, not a correctness bug).
- Without **C**, a fast automation can **read the view before** the projector has applied the causation event ⇒ **stale read** / duplicate work unless handlers are **idempotent** and/or you add **barrier** logic yourself.

### View-primary polling (D)

**Pros**

- **Diagram ↔ runtime** alignment: “processor reads this view” is literally the trigger.
- Fewer “which events should I subscribe to?” mistakes for authors who think only in **queues**.

**Cons**

- **Duplicate infrastructure**: scheduling, backoff, leader election, progress — either reimplement or generalize abstractly.
- **Cursor semantics**: event `position` vs **row version** / **last seen id** — harder to reason about ordering with **multiple** readers and **event-sourced** writes to the same view.
- **Risk of bypassing the log** as the mental model (“poll DB”) vs **event causality** for correlation and replay.

---

## Recommendation (for Crablet)

1. **Default product story:** stay on **event wake-up** (**A/B/E**), because it matches existing architecture and keeps causation in the store.
2. **“Support the todo model”** means **B** + prefer **E** for ergonomics: declare **`readsViews`**; **codegen or framework** infers **`wakeEventTypes`** from the union of those views’ **`reads`** lists, with **optional** `wakeEventsExtra` / `wakeEventsExclude` (names TBD) when inference is incomplete.
3. Validate **view names** exist; optionally **warn** when **`triggeredBy`** in YAML **contradicts** the inferred union (documentation / diagram accuracy).
4. **C (projection barrier)** as **opt-in** when stale reads are unacceptable (depends on `crablet-views` progress API).
5. **D (view-primary poller):** **not** the first implementation — **revisit after E** if **time-driven** work or **non-event** wake-up requirements remain painful; if added, **reuse** `EventProcessor` plumbing with a **specialized fetcher** and document cursor semantics (row id vs stream) explicitly.

---

## Work items (high level; not execution checklist)

- **Docs:** `crablet-automations/README.md`, **EVENT_MODEL_FORMAT**, **event-modeling skill** — document **E**: inferred wake events from **`readsViews`**, overrides, and when **D** might still be needed.
- **Schema / codegen:** **`readsViews`** on automation entries; implement **union-of-`reads`** inference for subscription; mirror in Java `AutomationSpec` if present in codegen model.
- **Runtime (optional phases):** validation warnings; then **projection barrier** helper or framework hook.
- **Diagram renderer:** edges **view → automation** already match this story once view names are linked to automations in YAML.

---

## FAQ: Do todo-based automations need a **dedicated** event poller?

**No** — not in the **recommended** design (event wake-up + read model inside `decide`).

**Today in spring-crablet, all of these already sit on the same generic [`crablet-event-poller`](../../crablet-event-poller) building blocks** (`EventProcessor`, fetch/handle/advance-cursor, optional leader election and shared-fetch):

| Module | Role of the poller |
|--------|-------------------|
| **crablet-views** | Projectors consume **stored events** matching each view’s selection; cursor per view/projection config. |
| **crablet-outbox** | Publishers consume **stored events** matching each topic’s `EventSelection`; cursor per `(topic, publisher)` pair. |
| **crablet-automations** | Handlers consume **stored events** matching each automation’s `getEventTypes()` / tags; cursor per **automation name**. |

Each module plugs in its own **`EventFetcher`**, **`EventHandler`**, **`ProgressTracker`**, and **`ProcessorConfig`**, but the **scheduling, batching, backoff, and (optional) shared single-DB-fetch fan-out** are the **same engine** — not three different poller implementations from scratch.

**Todo-list automation** in that picture is still **automation poller + event subscription**: new events **wake** the processor; **`decide`** reads the **materialized read model** (todo view) and issues commands / no-ops. Optional future work (**projection barrier**) is a **coordination layer** between automation and view progress, not a second poller **product**.

A **separate** “view-row poller” (variant **D**) is **optional later** — see § *Option D and boilerplate*. Prefer **variant E** (inferred wake events) first to cut boilerplate **without** a second cursor model.

---

## Open design questions

- For **E**: if **`readsViews`** is set, should **explicit** `getEventTypes()` / YAML `triggeredBy` be **disallowed**, **merged** with inferred union, or **override** inference?
- Should **`readsViews`** imply **topology validation** only, or **enforce** event subscription ⊆ union of view `reads` event lists?
- Barrier: **per-handler** vs **global** projector ordering when multiple views feed one automation?
- Interaction with **shared-fetch** automations: barrier must not deadlock batching.

---

*End of plan draft.*
