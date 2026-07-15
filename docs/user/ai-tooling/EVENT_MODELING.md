# Event Modeling

This page is the reference for how Crablet uses Event Modeling in documentation and feature-slice
workflows.

## Core Rule

Event Modeling is a horizontal timeline.

- time flows left to right
- subsystem lanes group bounded areas such as wallet, notification, inventory, auth, or payment
- rows are semantic layers such as trigger, command, event, view, automation, and translation
- the board should not be drawn as a top-to-bottom flowchart

In Crablet docs, Event Modeling boards are used to explain feature slices before or alongside
`event-model.yaml`. They are a modeling aid, not a replacement for the YAML contract in
[Event Model Format](EVENT_MODEL_FORMAT.md).

Scenarios captured during the workshop can be written directly into the `scenarios` section of
`event-model.yaml`. The generator turns each one into a JUnit 5 test scaffold.

## How To Read The Boards

Read the event row from left to right as the timeline of business facts.

Other rows sit above or below that timeline to show:

- what triggered the flow
- which command caused a fact to be recorded
- which views project from an event
- which automations wake up on an event
- which follow-up commands or translations happen downstream

In Crablet, those downstream consequences are normally asynchronous and poller-backed. A stored
event is committed first; views, automations, and outbox publication observe that committed event
later rather than participating in the synchronous command transaction.

The docs renderer has three layout modes (see [EVENT_MODEL_FORMAT.md](EVENT_MODEL_FORMAT.md) under **Diagram**):

- **Flat** — no `diagram.actors` and no `diagram.lanes`: one vertical stack of semantic rows.
- **Horizontal swim-stack** — `diagram.lanes` **without** `diagram.actors`: stacked horizontal bands per lane (trigger/command/event/… per band).
- **Canonical blueprint** — `diagram.actors` present: human **External** row (triggers with no `actor`) and declared **actors** above, **automated processor** bands from merged automations, **one** shared event timeline, and `diagram.lanes` only for the **bottom** section (views, outbox, synthetic commands). Structural commands are drawn in actor bands, not via `assignments`.

Declare `diagram.lanes` / `diagram.assignments` / `diagram.actors` in the main file when they reflect facts about the domain; put triggers, badges, synthetics, and diagram-only automation overlays in the sidecar when they are documentation-only additions.

When a board is illustrative rather than exhaustive, the docs should say so explicitly.

## Example Boards

### Minimal Slice

The first loan slice shows the smallest useful board: trigger, command, event, and one read model.

![Submit Loan Application event modeling board](../assets/loan-submit-event-modeling-board.svg)

This board is intentionally limited to the first observable outcome. It does not add automation or
outbox behavior because that sample does not define them. The view shown there is an asynchronous
projection from the committed event.

Source context:
- [Feature Slice Workflow](FEATURE_SLICE_WORKFLOW.md)
- [loan-submit-feature-slice-event-model.yaml](../examples/loan-submit-feature-slice-event-model.yaml)

### Reactive Slice

The wallet board shows a richer slice where one committed event fans out into:

- a query view
- an automation
- a follow-up command and follow-up event
- an illustrative outbox publication path

![Wallet opened automation and outbox event modeling board](../assets/wallet-opened-automation-and-outbox-board.svg)

The outbox branch in this board is intentionally illustrative. It is drawn from `WalletOpened` to
match the sample’s teaching goal, not to claim that every later event consequence is shown. The
view, automation, and outbox rows all represent asynchronous poller-backed work that happens after
`WalletOpened` is stored.

Source context:
- [Feature Slice Workflow](FEATURE_SLICE_WORKFLOW.md)
- [Wallet Example App](../../../examples/wallet-example-app/README.md)

## Relationship To Other Docs

- [Feature Slice Workflow](FEATURE_SLICE_WORKFLOW.md) is the hands-on guide that uses these boards
  in context
- [AI-First Workflow](AI_FIRST_WORKFLOW.md) explains the overall generation loop
- [Event Model Format](EVENT_MODEL_FORMAT.md) defines the YAML contract consumed by codegen
