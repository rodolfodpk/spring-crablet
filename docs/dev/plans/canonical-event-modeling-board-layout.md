# Plan: Canonical Event Modeling board layout

**Status:** Draft — implementation-ready once maintainers approve the locked decisions below.

## Context

Compared to the canonical Event Modeling blueprint ([What is Event Modeling — blueprint diagram](https://eventmodeling.org/posts/what-is-event-modeling/)), the Crablet docs renderer today uses **one stacked set of semantic rows** (trigger, command, event, view, automation, outbox) with optional **subsystem lanes**.

This plan introduces a **blueprint-aligned layout** activated when **`diagram.actors`** is present: **human/automated actor bands above**, a **single shared event timeline**, and **subsystem bands below** for read-side state and integration.

It **supersedes** “horizontal swim lanes everywhere” as the **primary multi-subsystem story** when actors are modeled. Horizontal swim-stack layout (`renderWithLanes`, bands per subsystem with embedded rows) remains a **fallback** when there are subsystem **lanes** but **no actors** (`lanes.length > 0 && actors.length === 0`). Pure flat rendering stays when **`actors` empty and `lanes` empty** after merge.

---

## Locked decisions

1. **`diagram.lanes` in blueprint mode** means **subsystem bands below the timeline only**. They partition **views, outbox publishers, and synthetic-command cards** driven by **`assignments`**. They do **not** partition the event timeline; all domain events remain in **one horizontal row**.
2. **Human-issued commands** (anything reached from a **`diagram.triggers[].linkedCommand`**) render in the **top actor/processor bands**, aligned horizontally with the column of **the first produced event**.
3. **Synthetic commands** are **not** human triggers. They render in the **bottom subsystem band** for the lane given by **`assignments[syntheticCommandName]`**, as today’s wallet semantics already imply. Automated **processor bands** arc to the timeline (and onward to that subsystem placement as needed)—no contradiction with rule (2).
4. **Assignments in blueprint mode** apply to **views, outbox entries, synthetic commands** (lane keys resolve to **`diagram.lanes[].id`**). **`assignments`** entries for structural **commands** defined in **`model.commands`** are **ignored** with a **`console.warn`** (authors should remove stale keys when migrating YAML).
5. **Triggers missing `actor`** use an implicit **`_external`** row (label **`External`**), appended **above human actors**. Order: **`External`** (if needed) → declared **`diagram.actors` (top to bottom)** → **automation processors** (see below).
6. **Subsystem band when actors exist but `diagram.lanes` empty:** synthesize exactly **one** implicit lane with `id`: **`_default`** (`label`: `model.domain || "Subsystem"`) **only when** there is at least one **view** or **outbox** to render; otherwise **omit** the bottom section entirely (`actors + event timeline only`).
7. **`TriggerSpec.actor` optional** everywhere (Java **`String` nullable**, YAML omit → External).

---

## Gaps addressed (versus old draft)

| Issue | Resolution |
|-------|------------|
| Synthetic in Notification lane vs “bottom is views/outbox only” | Bottom bands include **views, outbox, synthetics assigned to lanes** (locked decision 1–3). |
| Automations elevated “automatically” | One band per **`model.automation`** merged with **`diagram.automations`** (merge-by-name precedence unchanged), sorted **deterministically**: first by **`triggeredBy` event column index (left‑to‑right)**, tie-break **`automation.name`**. Rows appear **below human actors**, in that order (locked decision 5 order: External → actors → processors). Label: **`automation.name`**; subtitle shows **`on triggeredBy → emitsCommand`**. |
| Blueprint vs horizontal-swim wording | **`actors ? canonical blueprint : lanes ? horizontal swim fallback : flat`** documented in Fallback matrix below. |

---

## Target layout (conceptual)

```text
[Title + legend]

[── External (only if triggers lack actor) ───────────────────]

[── Manager ───────────────────────────────────────────────────]
      [trigger] [trigger] …     [commands above event columns …]

[── Guest ─────────────────────────────────────────────────────]
      …

[── CheckoutProcessor (automated) ─────────────────────────────]
      [WalletOpenedAutomation  on WalletOpened → SendWelcomeNotification]

══════════════════ EVENT TIMELINE ═══════════════════════════════
 [Event1][Event2][Event3] …   (single full-width strip)

[── Wallet subsystem ─────────────────────────────────────────]
       [WalletBalance view] … [WalletEventPublisher outbox]

[── Notification subsystem ──────────────────────────────────]
       [SendWelcomeNotification synthetic]

```

---

## YAML additions

### `diagram.actors`

```yaml
diagram:
  actors:
    - id: manager
      label: Manager
    - id: guest
      label: Guest
```

Order is **top-to-bottom**.

### Trigger `actor` (optional)

```yaml
diagram:
  triggers:
    - name: "Admin defines course"
      linkedCommand: DefineCourse
      actor: manager
```

Omitted → **External** row.

### Processor rows (automations)

**No YAML shape change.** Processors derive from **`model.automations` ∪ merged `diagram.automations`** (same precedence as today's merge helper).

### Lanes (`diagram.lanes`)

Unchanged schema; semantics **narrowed in blueprint mode** to **bottom-only** partitioning (**views**, **outbox**, **assigned synthetics**).

---

## Merge rules (`mergeEventModelForDiagram`)

Extend the existing precedence model:

| Key | Behavior |
|-----|----------|
| `actors` | Same rule as **`lanes`** / **`triggers`**: if **sidecar** defines the property, replace; else **diagram** replaces **model** baseline; baseline default **empty list**. |

**After merge**, **`render`** chooses layout by **`merged.actors.length`**, **`merged.lanes.length`** (flattened **`diagram`** already applied).

---

## Renderer concepts

Replace ad-hoc **`ROWS`** only in **canonical (actors)** path:

| Region | Contents |
|--------|----------|
| **TOP** | Bands: `[External]` (optional) + **`diagram.actors`** + automation processor bands |
| **EVENT** | One **timeline** band (existing event-card layout, full width, single row) |
| **BOTTOM** | Subsystem lane bands (**views**, **outbox**, **synthetic commands** placements) |

**Constants when `flatFallback`:**

Use existing **`ROWS`** order for **pure flat mode** unchanged.

---

## Functions (sketches)

### `shouldUseCanonicalLayout(model)`

`true` when **`(merged.actors || []).length > 0`**.

### `buildActorLayout(model)`

- Bucket triggers by **`actor || '_external'`** (resolve labels from **`diagram.actors`** for non-external IDs).
- For each bucket: associate **linked commands**, compute **timeline column index** via **first **`produces`** event** placement (today’s **`eventIndex`** logic).
- Build **AUTOMATION** processor list with stable sort (**triggeredBy** column, then **`name`**).
- Build **BOTTOM** placements: filter **views** / **outbox** / **synthetic placements** keyed by **`assignments`**; inject **`_default`** lane rule per locked decision **6**.
- Emit geometry: **`actorTopY`**, **`eventRowY`**, **`laneTopY`** with section constants (`laneHeaderH`, padding, …).

### `renderWithActors(model, container)`

Orchestrate background bands, TOP cards/arrows, EVENT row (= today’s horizontal strip), then BOTTOM (reuse spanning view/outbox arrows **within timeline columns** — **omit** arrows from timeline events in **different** subsystem assignment when those targets live in separate bottom bands except where same column).

### Path selection

See **Fallback matrix** below.

---

## Fallback matrix

| `actors.length` | `lanes.length` (after implicit `_default` expansion) | Path |
|-----------------|-------------------------------------------------------|------|
| 0 | 0 | **Flat**: existing **uniform `ROWS`** |
| 0 | > 0 | **Horizontal swim-stack** (**current `renderWithLanes` / lanes plan**) |
| > 0 | ≥ 1 (or implicit `_default` only) | **Canonical blueprint** (this doc) |

---

## Course example (modeling correction)

Multi-entity DCB teaches **different aggregates**, not **parallel timelines**. **Course** YAML should reflect **one** **event timeline**:

- **`diagram.lanes` removed** where they only partitioned “course vs student”; keep **Assignments** unset for commands.
- **`diagram.actors`** with **Admin** and **Student** (or **`manager`** / **`guest`**) triggers for the three intents.
- **Single** **`CourseAvailability`** view in **`BOTTOM`** (**implicit `_default`** lane acceptable).

---

## Wallet example alignment

| Region | Wallet-specific content |
|--------|--------------------------|
| **TOP** | **Customer** (or **`guest`**) actor row with triggers for open/deposit/withdraw/transfer-linked commands (commands sit above produced event columns); **WalletOpenedAutomation** processor band after human actors |
| **EVENT** | Ordered wallet events (**WalletOpened**, … **MoneyTransferred**) **one timeline** |
| **BOTTOM** | **`wallet` lane**: **WalletBalance** view, **WalletEventPublisher** outbox — **`notification` lane**: **`SendWelcomeNotification`** **synthetic** card |

Synthetic placement uses **`assignments.SendWelcomeNotification: notification`** (unchanged semantics).

---

## Files to change

- [`docs/event-model-renderer.js`](../event-model-renderer.js) — `mergeEventModelForDiagram`, `shouldUse*` guards, **`buildActorLayout`**, **`renderWithActors`**, branch between flat / **`renderWithLanes`** / **`renderWithActors`**.
- [`docs/examples/course-event-model.yaml`](../examples/course-event-model.yaml) and [`docs/examples/course-diagram.yaml`](../examples/course-diagram.yaml) — actors + triggers; strip obsolete lane-only partitioning.
- [`docs/examples/wallet-event-model.yaml`](../examples/wallet-event-model.yaml) and [`docs/examples/wallet-diagram.yaml`](../examples/wallet-diagram.yaml) — actors + **`actor`** on triggers where applicable.
- [`docs/user/examples/event-model-schema.json`](../user/examples/event-model-schema.json) — **`ActorSpec`**, optional **`actor`** on trigger entries under **`diagram`**.
- [`embabel-codegen/src/main/java/com/crablet/codegen/model/`](../../embabel-codegen/src/main/java/com/crablet/codegen/model/) — **`ActorSpec`**, **`TriggerSpec(actor)` nullable**, **`DiagramSpec.actors`** (partially landed in repo — finish call sites/tests).

---

## Suggested execution order

1. **Java + tests** — **`ActorSpec`**; **`TriggerSpec(actor)` nullable** everywhere; **`DiagramSpec`** **`actors`** + **`DiagramSpec.empty()`**; **`SchemaResolver`/tests** preserve **`diagram`**. Run **`rg "new DiagramSpec("`** / **`rg "new TriggerSpec("`** inside **`embabel-codegen`**.
2. **JSON Schema** — document **`diagram.actors`** and optional **`trigger.actor`** (and allowed assignment keys descriptions if present).
3. **Renderer** — merge extension; **`renderWithActors`**; warn on invalid command lane assignments.
4. **Examples + HTML** sanity (wallet/course **`8091`** if using local static server conventions from prior plans).
5. **Docs/skills** — **`EVENT_MODEL_FORMAT.md`**, **`EVENT_MODELING.md`**, **`event-modeling`** skill: blueprint vs flat vs lanes-only-horizontal.

---

## Acceptance

### `/course.html` (representative checklist)

- [ ] **TOP**: **Admin** band with triggers for **`DefineCourse`**, **`ChangeCourseCapacity`**; **Student** band with **`SubscribeStudentToCourse`**; commands aligned above correct event columns **before** timeline.
- [ ] **EVENT**: **Three** timeline cards in **one** row: **`CourseDefined`**, **`CourseCapacityChanged`**, **`StudentSubscribedToCourse`** (**multi-entity DCB** badge if configured).
- [ ] **BOTTOM**: **Implicit** or omitted lane as per rules; **`CourseAvailability`** view **below** events; arrows only from timeline events consistent with subsystem band rules (**no phantom cross-student/course partitioning**).

### `/wallet.html`

- [ ] **TOP**: Customer actor triggers (four intents) + **`WalletOpenedAutomation`** processor band (after human actors unless spec says opposite—locked order: External → **`actors`** → processors).
- [ ] **EVENT**: Full wallet timeline in **one** row.
- [ ] **BOTTOM**: **WalletBalance** view + **WalletEventPublisher** in **wallet** band; **`SendWelcomeNotification`** synthetic card in **notification** band (**SendWelcomeNotification** not duplicated in TOP unless also manually triggered—normally **not**).

---

## Maintainer notes

- **Course vs wallet** pedagogy differs: course shows **timeline unification**, wallet shows **cross-subsystem processor + synthetic** in **BOTTOM**.
- Updating **[`event-modeling-lanes-and-rows.md`](event-modeling-lanes-and-rows.md)** with a banner that **`lanes`** semantics split by layout mode (**columns vs bottom-only**) avoids regressions when reading older plan text.
