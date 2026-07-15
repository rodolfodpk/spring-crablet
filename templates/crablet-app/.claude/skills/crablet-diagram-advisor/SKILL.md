---
name: crablet-diagram-advisor
description: >
  Use this skill when adding diagram metadata to event-model.yaml for multi-lane or actor-based
  boards. Covers diagram.actors, diagram.lanes, diagram.assignments, sidecar distinction, and the
  rule that Java codegen ignores diagram: entirely.
  Do not use for event-model.yaml workshop design (use event-modeling) or app feature slices
  (use crablet-app-dev).
---

# Crablet Diagram Advisor

This skill covers how `diagram:` metadata in `event-model.yaml` affects the rendered docs board.

## Key Rule

Java codegen ignores the `diagram:` key entirely. Everything under `diagram:` is renderer and
tooling metadata only — it does not change generated Java code.

## When to Add Diagram Metadata

- Two or more bounded contexts or subsystems in the model → add `diagram.lanes` and `diagram.assignments`.
- You want named actor bands on the board → add `diagram.actors`.
- Docs-only visual context (triggers, synthetic commands, badges) → use a `*-diagram.yaml` sidecar.

## Diagram Keys

| Key | Purpose |
|-----|---------|
| `diagram.actors` | Names actor bands for canonical layout; each actor gets a horizontal band |
| `diagram.lanes` | Names bounded-context or subsystem groupings |
| `diagram.assignments` | Routes commands, events, and views into specific lanes |
| `diagram.display.publicationEdges` | Set `true` to show outbox publication edges (hidden by default) |

## Lane Example

```yaml
diagram:
  lanes:
    - name: wallet
      label: Wallet Service
    - name: notification
      label: Notification Service
  assignments:
    commands:
      OpenWallet: wallet
    events:
      WalletOpened: wallet
      WelcomeNotificationSent: notification
```

Time flows left to right across all lanes on the same timeline.

## Sidecar Files

A `*-diagram.yaml` sidecar holds docs-only visual overlays that do not belong in the structural
model: trigger cards, synthetic command nodes, event badges. Keep structural model data in
`event-model.yaml`; put docs-only context in the sidecar.


**Status:** pré-1.0/experimental — ver `docs/dev/PRODUCT_ROADMAP.md` para critérios de maturidade.
