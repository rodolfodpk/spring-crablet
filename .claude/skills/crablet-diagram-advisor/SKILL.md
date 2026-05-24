---
name: crablet-diagram-advisor
description: >
  Use this skill when authoring or reviewing event-model docs diagrams and renderer projections.
  Covers diagram.actors, diagram.lanes, diagram.assignments, sidecar overlays, arrow rules, and
  renderer vocabulary. Root version includes renderer internals for docs maintainers.
  Do not use for event-model.yaml workshop design (use crablet-event-modeling) or app feature slices
  (use crablet-app-dev).
---

# Crablet Diagram Advisor

This skill covers the HTML docs diagram renderer (`docs/event-model-renderer.js`) and how
`event-model.yaml` maps to the SVG visual output.

## Routing

- For Event Modeling workshop dialogue and `event-model.yaml` shape, use `event-modeling`.
- For app feature-slice implementation, use `crablet-app-dev`.
- Use this skill when: adding `diagram:` metadata to an event model, reviewing an existing board,
  authoring a multi-lane board, deciding sidecar vs main model, or debugging renderer output.

## Key Rule

Java codegen ignores the `diagram:` key entirely. Everything under `diagram:` is renderer and
tooling metadata only — it does not change generated Java code.

## Diagram Key vs Codegen

| Key | Codegen | Renderer |
|-----|---------|----------|
| `events`, `commands`, `views`, `automations`, `outbox` | Yes | Yes |
| `diagram.actors` | No | Yes |
| `diagram.lanes` | No | Yes |
| `diagram.assignments` | No | Yes |
| `*-diagram.yaml` sidecar | No | Yes (merged) |

## Layout Modes

### Default (rows mode)

No `diagram.actors` key. All elements render in row bands: trigger, command, event, view,
automation, outbox. Columns come from the renderer's topological sort of the event timeline.

### Canonical (actors mode)

`diagram.actors` is present. Each actor gets its own horizontal band. Within each band, elements
stack vertically: wireframe (top), commands, events, views. Automations appear as **⚙** labels
inside actor or processor bands — not as orange swim-lane tiles.

## Lanes (Bounded Contexts)

Add `diagram.lanes` and `diagram.assignments` when the model has two or more bounded contexts or
subsystems (e.g. wallet + notification). Lanes are bounded-context groupings; rows are
element-type layers within a lane.

```yaml
diagram:
  actors:
    - id: customer
      label: Customer
  lanes:
    - name: wallet
      label: Wallet Service
    - name: notification
      label: Notification Service
  assignments:
    commands:
      OpenWallet: wallet
      SendWelcomeNotification: notification
    events:
      WalletOpened: wallet
      WelcomeNotificationSent: notification
```

Time flows left to right across all lanes on the same timeline.

## Arrow Rules (Canonical / Actors Layout)

Arrowheads point at the target. All arrows are directed.

| Arc | Stroke |
|-----|--------|
| Wireframe → command | Solid, downward (crosses actor band) |
| Command → event | Solid, downward |
| Event → view (same lane, aligned column) | Solid, downward |
| Event → view (cross-lane or offset column) | Dashed + elbow path |
| View → actor wireframe | Solid, upward (feedback — what the actor sees) |
| View → automation | Dashed, upward (async / poller-backed, not synchronous RPC) |

## Header Legend (Canonical Mode)

On actor boards the swatch row shows **Command / Event / View** only. There is no Automation color
tile — automations appear as **⚙** labels, not fill-color tiles.

## Outbox Publication Edges

Dashed `publication → …` edges from `outbox.handles` events are **hidden by default** (integration
overlay, not core Event Modeling notation). Enable with `diagram.display.publicationEdges: true`.

## Sidecar Files (`*-diagram.yaml`)

Use a sidecar for purely docs-specific overlays that do not belong in the structural model:
- Trigger cards (illustrative UX wireframes)
- Synthetic command nodes connecting bounded contexts
- Event badges and annotations
- Cross-context automation rows that cannot be codegen inputs

Sidecar merge rules are in `docs/user/ai-tooling/EVENT_MODEL_FORMAT.md`. Keep structural model
data in `event-model.yaml`; keep docs-only visual context in the sidecar.

## Renderer Internals (Maintainer Reference)

The renderer is `docs/event-model-renderer.js`. It reads `event-model.yaml` (and an optional
`*-diagram.yaml` sidecar) and outputs inline SVG.

**Column order** derives from a Kahn topological sort of event relationships:
- `guardEvents` → produced events define ordering edges
- `produces[i]` → `produces[i+1]` for commands with multiple produced events
- `automation.triggeredBy` → the first produced event of `automation.emitsCommand`

Stable tie-break: smallest original `events[]` index wins. Cycles produce a console warning and
fall back to YAML declaration order.

**Constants:** Color palette is `C`, layout constants are `L`, canonical layout constants are
`CAN`. Do not hard-code pixel values in docs prose or tests; reference these constants by name.

**Row order** in default mode: `trigger → command → event → view → automation → outbox`.
In canonical mode rows are per-actor bands stacked vertically in the same semantic order.
