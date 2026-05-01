# Event Modeling Lanes And Rows Plan

## Context

Crablet now has two related but inconsistent diagram concepts:

- The `event-modeling` Claude skill correctly describes Event Modeling swim lanes as subsystem or bounded-context lanes, such as inventory, auth, payment, wallet, or notification.
- The current hand-authored SVGs and `docs/event-model-renderer.js` use "lanes" for semantic layers such as trigger, command, event, view, automation, and outbox.

This creates ambiguity in docs and tooling. We need to align vocabulary and renderer behavior without breaking the existing simplified diagrams.

## Goal

Support proper Event Modeling subsystem lanes while preserving the current Crablet-style semantic rows.

Time still flows left to right. Semantic element types remain stacked vertically. Optional subsystem lanes group timeline content by bounded area or subsystem.

## Vocabulary

Use these terms consistently:

| Term | Meaning | Examples |
|---|---|---|
| Row | Semantic element layer | trigger, command, event, view, automation, outbox |
| Lane | Subsystem or bounded-context grouping | wallet, notification, auth, payments, inventory; course diagram: course vs student lanes for multi-entity DCB |
| Slice | Vertical feature cut through rows, optionally crossing lanes | open wallet, subscribe student |

Do not call trigger/command/event/view layers "lanes" in new docs or code comments. Existing SVG label text can remain until those assets are regenerated or replaced.

## Design

Keep clean codegen YAML unchanged. Subsystem lane data belongs only in renderer sidecar files, such as `docs/examples/wallet-diagram.yaml`.

Example sidecar extension:

```yaml
lanes:
  - id: wallet
    label: Wallet
  - id: notification
    label: Notification

assignments:
  OpenWallet: wallet
  WalletOpened: wallet
  WalletBalance: wallet
  WalletOpenedAutomation: wallet
  SendWelcomeNotification: notification
```

Renderer behavior:

- If no `lanes` are present, render exactly as today.
- If one lane is present, treat it as optional metadata. Prefer omitting `lanes` for simple
  single-context diagrams unless the page intentionally wants a visible bounded-context label.
- In multi-lane mode, subsystem lanes partition the x-axis into contiguous horizontal timeline
  sections, ordered by `lanes[]`. Semantic rows remain the y-axis. This is the canonical v1
  geometry: lanes are not top-only labels, and they do not replace the trigger/command/event rows.
- Columns are allocated within each subsystem lane in event order. The renderer then lays out lane
  sections left to right. Cross-lane arcs may cross section boundaries.
- Lane section width is deterministic: `max(1, laneColumnCount) * COL_W`, plus the same left/right
  padding used by the current grid. Empty lanes render as one-column sections so labels remain
  visible, but authors should avoid empty lanes in checked-in examples.
- Lane IDs in `lanes[].id` are exact, case-sensitive keys. Every `assignments` value must match
  one declared lane ID.
- Cards keep their semantic row placement and are visually grouped by subsystem assignment.
- Cross-lane automation and translation arcs are allowed and should remain readable.
- Missing assignments warn in the console when multiple lanes exist, but rendering continues.

Assignment rules:

- Assignment keys are stable model element names only: command names, event names, view names,
  automation names, outbox entry names, and synthetic command names.
- Do not support trigger display names as assignment keys in v1. A trigger inherits the lane of
  its `linkedCommand`.
- In multi-lane mode, views, automations, and outbox entries should be explicitly assigned because
  they can span multiple event columns.
- Spanning cards belong visually to one lane in v1. If a view or outbox reads/handles events across
  multiple lanes, the renderer still places the card in its assigned lane and logs a warning that
  the span crosses lane partitions. Do not draw one card across multiple subsystem sections in v1.
- If a spanning element is unassigned, fall back to the lane of its first anchor and warn:
  - view/outbox: first referenced event in `reads` or `handles`
  - automation: `triggeredBy` event
  - synthetic command: first automation that emits it
- If the fallback anchor cannot be resolved, place the card in the first declared lane and warn.

## Implementation Steps

1. Rename renderer internals:
   - `LANES` -> `ROWS`
   - `laneH` -> `rowH`
   - `laneTopY()` -> `rowTopY()`
   - `renderLanes()` -> `renderRows()`
   - Keep external behavior unchanged.

2. Extend diagram sidecar parsing:
   - Read optional `lanes` list.
   - Read optional `assignments` map.
   - Merge both into the renderer input alongside `triggers`, `syntheticCommands`, and `eventBadges`.

3. Add assignment resolution:
   - Valid assignment keys include command names, event names, view names, automation names,
     outbox entry names, and synthetic command names.
   - Triggers inherit the lane of their `linkedCommand`; trigger display strings are not valid
     assignment keys in v1.
   - When multiple lanes are present, warn for rendered cards without assignment.
   - For unassigned spanning elements, apply the fallback anchor rules from the Design section and
     warn so authors can add explicit assignments later.
   - Warn for assignment keys that do not resolve to any rendered element.

4. Render subsystem lanes:
   - Preserve the current row layout as the default.
   - In multi-lane mode, partition the x-axis into contiguous subsystem lane sections. Each section
     keeps the same semantic rows.
   - Compute section widths with `max(1, laneColumnCount) * COL_W`; do not use proportional scaling.
   - Add subtle lane headers and low-contrast background bands that make the assigned subsystem
     visible without turning the board into a top-to-bottom flowchart.
   - For cross-partition spans, keep the card inside its resolved lane and warn. Use arrows/labels
     to show cross-lane relationships instead of stretching cards across lane sections.
   - Keep the visual style close to the existing SVG chrome: cream background, restrained lines, Trebuchet/Segoe text.

5. Update examples:
   - Wallet:
     - `wallet`
     - `notification`
     - Assign `SendWelcomeNotification` to `notification`; main wallet commands/events/views stay in `wallet`.
   - Course (`docs/examples/course-diagram.yaml`):
     - Use two subsystem lanes to teach **multi-entity DCB**: `course` (Course) vs `student` (Student enrollment).
     - Assign course-scoped commands/events/view to `course`; subscription command and spanning event to `student`.
     - Triggers stay human-readable (`Admin defines course`, etc.); they inherit lanes via `linkedCommand`, not trigger text.
     - A no-lane variant remains valid for minimal diagrams, but the checked-in course diagram keeps lanes so `docs/course.html` matches the wallet pattern (sidecar-driven partitions).

6. Update docs:
   - `CLAUDE.md`: add a short Event Modeling vocabulary note.
   - `.claude/skills/event-modeling/SKILL.md`: keep subsystem-lane guidance, but mention Crablet renderer sidecar assignments.
   - `docs/user/ai-tooling/EVENT_MODELING.md`: distinguish Crablet's simplified semantic-row boards from full Event Modeling subsystem lanes.
   - `docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`: replace ambiguous "lanes" wording with "rows" where it refers to trigger/command/event/view.
   - `docs/examples/concepts.md`: keep subsystem swimlane explanation, but add the row/lane distinction.
     Verify this path exists during implementation; if the concepts source moves or is generated,
     update the actual source file instead of creating a duplicate.
   - Use a grep checklist during implementation: search for `lane`, `swim`, `trigger`, `command`,
     `event`, and `view` in `CLAUDE.md`, `.claude/skills/event-modeling/SKILL.md`,
     `docs/user/ai-tooling`, and `docs/examples`.

## Verification

Reference checks:

- Existing diagrams without `lanes` render unchanged.
- Wallet sidecar lane assignments resolve without unknown-key warnings.
- Course: `course-diagram.yaml` declares `course` + `student` lanes; assignments resolve without spurious unknown-key warnings (same bar as wallet).
- Renderer logs warnings for anonymous synthetic commands and missing lane assignments when appropriate.
- Trigger assignment strings are rejected or warned as unknown assignment keys; triggers inherit from
  `linkedCommand`.
- Unassigned spanning cards use the documented fallback anchor and log warnings.
- Cross-partition spans log warnings and remain visually contained in one lane section.
- Unknown assignment lane IDs warn and fall back to the first declared lane.

Visual checks:

- `docs/wallet.html` shows the notification command as distinct from the wallet lane.
- Cross-lane automation from `WalletOpened` to `SendWelcomeNotification` remains readable.
- `docs/course.html` remains focused on multi-entity DCB; with the sidecar, lane bands should separate course operations from student enrollment while keeping semantic rows.
- No text overlaps on desktop widths used for docs and GitHub Pages.

Parser checks:

- `docs/examples/*-event-model.yaml` still parse through embabel-codegen planner/parser.
- Diagram sidecar files are never treated as codegen input.

## Out Of Scope

- Full drag-and-drop diagram editing.
- Pixel-identical recreation of existing SVGs.
- Actor lanes as a separate first-class concept.
- Automatic bounded-context inference from package names.
- Codegen changes to the official `event-model.yaml` schema.
