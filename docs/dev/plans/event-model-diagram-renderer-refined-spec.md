# Plan: Event model diagram renderer — refined canonical layout & customization

**Status:** Draft — saved 2026-05-02 from maintainer/workshop dialogue.

**Purpose:** Single place for layout rules, arrow semantics, outbox notation, and user-facing diagram customization — separate from codegen (`events`, `commands`, `views`, …). Renderer work tracks this doc; [`canonical-event-modeling-board-layout.md`](canonical-event-modeling-board-layout.md) remains historical context but **differs on command placement** (see § Relationship).

---

## Repo status (snapshot)

| Area | State |
|------|--------|
| **Java** | `ActorSpec`, `DiagramSpec`, `LaneSpec`, `TriggerSpec` (nullable `actor`), `SyntheticCommandSpec` — new/updated types in working tree under `crablet-codegen` |
| **Renderer** | `docs/event-model-renderer.js` — canonical path needs `diagram.actors` + `trigger.actor` wired through merge + layout |
| **Examples** | `wallet-event-model.yaml` / `course-event-model.yaml` (+ optional sidecars) — add actors and per-trigger `actor` to exercise canonical rendering |

---

## YAML boundary

- **`diagram:` stays under `event-model.yaml`** (or merged sidecar). Top-level keys remain **codegen inputs** that produce artifacts; **`diagram` is renderer-only** and codegen ignores it (or parses and discards for validation only).
- **Actors are domain vocabulary** (Storyline / handler kind) but **do not generate Java** from actor rows alone — still appropriate under `diagram:` to keep the “top-level = codegen” contract clear.
- **Prefer one file:** consolidate `diagram.actors` + `diagram.triggers` in the main model when possible; sidecar merge rules stay **base model < `diagram:` < sidecar** (arrays replace, maps shallow-merge) where sidecars remain.

---

## Canonical layout (agreed rules)

### Regions

1. **Time header** — left-to-right axis; columns shared by all bands and lanes.
2. **Actor / processor bands (top)** — one **full-width horizontal band** per human actor, external actor, or automation processor. Order: **External** (if any trigger lacks `actor`) → **`diagram.actors` top-to-bottom** → **automation processors** (deterministic sort, e.g. by `triggeredBy` column then name).
3. **Context lanes (below)** — bounded-context / subsystem **swimlanes**. **Commands, events, and read-model views** are laid out **inside** these lanes, not in a separate global “shared” CMD/EVT strip.

### What may be a rectangle

**Inside the context lanes, rounded/box cards are only:**

- **Commands** (blue / command palette in theme)
- **Events** (orange)
- **Views** — read models (green)

**Uniform sizing:** command, event, and view cards use the **same width/height**; **only color** distinguishes type. No text row labels like `CMD` / `EVT` / `VIEW`.

### Command ↔ event stacking (timeline within a lane)

- For each command, its **`produces`** primary event (per modeling) sits **directly below** the command, **same left alignment**, connected by a **vertical** edge (▼).
- **Horizontal separation** between **command/event stacks** carries **time** / sequence — not by offsetting the event to the right of the command within the same stack.

### Actor band content (not rectangles)

- **Wireframes:** sketch notation (e.g. ≡ + horizontal dashes), **not** boxed UI mockups.
- **Automation row:** **⚙** + short label/description; **not** a rectangle for “the processor”.
- **No command cards** in the actor band — commands live **in the lane**; the wireframe connects **down** into the lane’s command card (vertical drop through the band separator).

### Arrows (directed edges)

| Connection | Direction |
|------------|-----------|
| Actor wireframe → command | Downward into lane |
| Command → produced event | Downward, same stack |
| Event → view(s) that **read** that event | Downward (within lane or cross-lane) |
| **View (read model) → actor or processor that reads it** | **Upward** (from view card to wireframe / automation band) |
| Cross-lane dependencies | Same logical rules; style (solid vs dashed) is implementation-defined but must be **unambiguous** |

**Rule of thumb:** read models are **fed by events below** and **feed humans/automation above**.

---

## Outbox and external async (reliable path = Option B)

- **Outbox remains in `event-model.yaml` for Crablet** (codegen, runtime).
- **On the Event Modeling diagram:** the **outbox publisher is not a rectangle**. Do **not** place outbox cards in the lane card grid.
- **Do show reliable external output** as **notation**: e.g. an edge or “publication” glyph from the relevant **domain event** (or from the stack that produced it) **across the system boundary** toward the external system (Fraud Detector Engine, etc.).
- **Option B (chosen for fraud example):** automation reads a **todo-list / queue read model**, issues a **command** that **produces an event** consumed by **outbox**, which delivers to the external engine — **at-least-once**. The **event** rectangle is on the board; the **integration arrow** expresses outbox delivery without a second “outbox box” card.
- **Option A** (direct HTTP from automation, no outbox) is **not** the reference story for that domain; if modeled elsewhere, it still uses **notation**, not an outbox rectangle.

### External actor callback

- **Systems that push back in** (async HTTP callback) appear as **actor bands** with wireframe + trigger semantics driving **`linkedCommand`** into the lane — **not** as automations, when they **originate** the command from outside.

---

## Automation vs views

- **Both** human actors and automation **read read models**; **arrows go from the view up** to the reader.
- **Todo-list pattern:** processor polls / reacts to a read model row; **decisions** (e.g. whether to call risk engine) can be **view-state-driven**, not hard-wired to a single upstream event in the diagram’s story.

---

## User customization surface (`diagram:`)

All optional; ignored by codegen.

| Key | Purpose |
|-----|---------|
| **`diagram.display.title` / `.subtitle`** | Override header text vs `domain` alone |
| **`diagram.display.legend`** | Show/hide legend row |
| **`diagram.display.colWidth`** | Column width in px (default renderer constant) |
| **`diagram.display.theme`** | Preset palette: `default` \| `blueprint` \| `high-contrast` — **themes**, not per-element color overrides (type = color invariant) |
| **`diagram.notes`** | Map element name → note text **inside** or **below** cards |
| **`diagram.eventBadges`** | Extend to **commands** as well as **events** (badges / subtitles) |
| **`diagram.eventOrder`** | Optional **pinned** event column order; listed events first in order, remainder **topo-sorted** from graph |
| **`diagram.actors` / `diagram.lanes` order** | **Top-to-bottom** band order (document only; no new keys) |

Sidecars may override `diagram.display` and maps per merge rules.

---

## Files to touch (implementation backlog)

| File | Change |
|------|--------|
| [`docs/event-model-renderer.js`](../event-model-renderer.js) | Canonical layout per § Canonical layout; arrow rules; outbox as **edge/glyph** only; read `diagram.display`, `diagram.notes`, command badges, `diagram.eventOrder`, themes |
| [`docs/user/examples/event-model-schema.json`](../user/examples/event-model-schema.json) | Schema for `display`, `notes`, `eventOrder`, theme enum, command badges |
| [`docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`](../user/ai-tooling/EVENT_MODEL_FORMAT.md) | Document customization keys + “no outbox rectangle” rule |
| [`.claude/skills/event-modeling/SKILL.md`](../../.claude/skills/event-modeling/SKILL.md) | When emitting YAML, suggest `diagram.display.title` if display name ≠ `domain` |
| Examples | `wallet-event-model.yaml` / `course-event-model.yaml` — actors, `actor` on triggers, optional `display` / `notes` for manual QA |

---

## Verification suggestions

1. **Wallet/course HTML:** open `docs/wallet.html` / `docs/course.html` after YAML updates — actor bands, lane-only rectangles, view→actor up-edges.
2. **Customization smoke:** `display.title`, `legend: false`, `colWidth`, `notes` on two events, `eventOrder` pin, `theme: blueprint`.
3. **`make install`** — Java tests green; renderer is client-side (no Maven dependency unless tests added later).

---

## Implementation strategy (refactor in place)

**Recommendation:** evolve [`docs/event-model-renderer.js`](../event-model-renderer.js); **do not** greenfield a second renderer. The file already owns merge rules, timeline ordering, lane mode, and ~1.9k lines of shared geometry and cards.

| Preserve | Rework |
|----------|--------|
| **Flat** layout path when `actors` and `lanes` are absent (after merge) | **`buildCanonicalLayout` + canonical render path** when `diagram.actors` is non-empty — today’s implementation follows the **older** spec (commands in top bands, single global event row, outbox as bottom cards) |
| **`renderWithLanes`** when `lanes` exist and `actors` is empty | Canonical placement so **commands + produced events** live **inside lanes**; **top bands** = wireframes / processor labels only; **outbox** = edge/glyph only, not a lane rectangle |
| **`mergeEventModelForDiagram`**, `computeEventTimelineOrder`, `renderCard` / arrow helpers | Arrow routing for **view → reader up**, cross-lane edges, and **external publication** notation; optional split of canonical code into a separate module **after** behavior matches |

**Execution order (suggested):**

1. Implement refined **lane stacks** (command above event, uniform card size) and **actor-only top** for the canonical branch.
2. Replace outbox **rectangle** placement in that branch with **publication notation** tied to the **outbox-handled event** (or agreed anchor per model).
3. Add **read-model → actor/processor** up-edges and tighten cross-lane styling (solid vs dashed documented in code comment + `EVENT_MODEL_FORMAT.md`).
4. Layer **`diagram.display`**, **`diagram.notes`**, **`diagram.eventOrder`**, command **`eventBadges`**, and **themes** on top of stable layout.
5. Update examples + schema + skill; manual HTML pass; then consider file split for readability only.

**Spec clarifications to lock during coding:**

- **Commands with multiple `produces` events** — define which event anchors the **column** and which appear as **secondary** boxes or badges (single primary + extras vs. multiple stacks).
- **`diagram.eventOrder`** — partial list pins first; remainder **topo-sorted**; document cycle / missing-edge behavior (`console.warn` + fallback).

---

## Relationship to `canonical-event-modeling-board-layout.md`

That plan describes **command cards in the top actor bands** aligned to event columns, plus a **single full-width event timeline row** and **bottom** views/outbox/synthetics.

**This refined spec supersedes that for:**

- **Command + produced event stacks live in context lanes** (not in the top band).
- **Top bands are wireframe / processor narrative only** — no command rectangles there.
- **Outbox is never a lane rectangle**; show **delivery notation** only; align with **Option B** event + outbox pipeline.

Keep **`shouldUseCanonicalLayout` / `actors.length > 0`** intent, but implementation geometry should follow **this** document’s **lane-contained stacks** and **uniform card sizes**.

---

## Related framework work

**Todo-list automations** (processor reads a queue-like **read model**) are a **modeling + config** goal for Crablet, not a separate poller type by default. Wake-up stays **event subscription**; “support” means **declaring** view reads, optional **projection barriers**, and docs/codegen alignment — see [`automation-todo-read-model.md`](automation-todo-read-model.md) for tradeoffs (event wake-up vs view-primary polling) and a recommended path.

---

## Reference domain (Loan / fraud) — narrative only

Used in workshop for edge cases; not required to be checked into repo as YAML.

**Actors:** Applicant, Fraud Detector Engine (external), FraudCheck processor (automation), Loan officer.  
**Read models:** `LoanApplicationReview`, `PendingFraudChecksQueue` (todo list feeding processor).  
**Flow:** events feed views down; views feed actors/processors up; command→event stacks in **Lending** vs **Risk** lanes; async gap visible on shared timeline; FDE callback modeled as external actor command intake.

---

*End of saved plan.*
