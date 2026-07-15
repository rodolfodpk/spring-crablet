# Plan: Horizontal Swim-Lane Renderer

**Status:** Implementation-ready.

## Context

The current renderer draws subsystem lanes as **vertical column groups** side by side. This differs
from canonical Event Modeling notation, where swim lanes are **horizontal bands** stacked
vertically — each subsystem owns a full row stack for the elements assigned to it.

This plan rewrites `docs/event-model-renderer.js` to the horizontal band style. The lane metadata
(`diagram.lanes`, `diagram.assignments`) already exists in the example YAML files and sidecars from
the `optional-event-model-diagram` work. Only the renderer changes.

## Locked Decisions

- **Row order** is the existing `ROWS` constant: `trigger → command → event → view → automation →
  outbox`. This order is preserved inside each lane band.
- **`model.lanes.length === 0`** → fall back to the current flat layout unchanged (no band headers).
- **`model.lanes.length === 1`** → single labeled horizontal band (lane-aware path).
- **All lanes share the same content width** determined by `max(laneColumnCount)` so bands align.
- **Cross-lane view/outbox arrows are omitted.** A view or outbox card is placed in its assigned
  lane; event-to-view and event-to-outbox arrows are only drawn for events in the same lane. A
  console warning fires for foreign-lane reads/handles (matching existing renderer behavior at
  `event-model-renderer.js:318,340`).
- **Cross-lane automation** (source lane ≠ target lane): render the automation card in the source
  lane's automation row; draw a cross-lane arc from the automation card to the target command card
  using `renderArc` (the existing bezier arc function). The arc's Y end-point is in the target
  lane's command row. Because `renderArc` already handles differing X positions, no special routing
  is needed beyond computing the correct `y2` from `rowTopYInLane` of the target lane.
- **`isRowVisibleInLane(laneData, 'command')`** returns true when the lane has real command
  placements **or** synthetic command placements. Notification's sole content
  (`SendWelcomeNotification`) is synthetic; the command row must still appear.
- **Assignment fallback rules** are preserved from `event-modeling-lanes-and-rows.md:70-85`:
  - Trigger inherits the lane of its `linkedCommand`.
  - View/outbox: fall back to lane of the first event in `reads`/`handles`; warn if cross-lane.
  - Automation: fall back to lane of `triggeredBy` event.
  - Synthetic command: fall back to lane of the first automation that emits it.
  - If no anchor resolves: place in `laneIds[0]` and warn.

## Target Visual

```
[Title + legend]

[══ Wallet ══════════════════════════════════════════════════════]
  TRIGGER    │ [Customer opens] [Deposits] [Withdraws] [Transfers]
  COMMAND    │ [OpenWallet] [Deposit] [Withdraw] [TransferMoney]
  EVENT      │ [WalletOpened][DepositMade][WithdrawalMade][MoneyTransferred]
  VIEW       │ [WalletBalance ───────────────────────────────────]
  AUTOMATION │ [WalletOpenedAutomation] ──────────────────────────── ↓ cross-lane arc
  OUTBOX     │ [WalletEventPublisher ──────]

[══ Notification ═════════════════════════════════════════════════]
  COMMAND    │ [SendWelcomeNotification]   ← arc lands here
```

## Files Changed

- `docs/event-model-renderer.js` — only file that changes.

No Java, schema, YAML, or HTML changes. Lane metadata is already present.

## New Layout Constants

Add to the `L` object in `event-model-renderer.js`:

```js
laneHeaderH:   28,   // height of the lane label strip at top of each band
lanePadBottom: 12,   // vertical padding below the last row of each band
```

## New Coordinate System

### `buildLaneLayout(model)` — new function

Groups elements by lane using `model.assignments` and the fallback rules above.

Per-lane allocation mirrors the existing logic inside `buildLayout` but scoped to each lane's
element subset:
- `eventIndex` within the lane (local column 0, 1, 2…)
- `commandPlacements`, `viewPlacements`, `automationPlacements`, `outboxPlacements`,
  `triggerPlacements`, `syntheticPlacements` per lane
- `laneColCount[laneId]` — how many columns that lane needs
- `totalCols = max(laneColCount values)` — all lanes share this width

Returns:
```js
{
  laneIds, laneLabels,
  laneData: { [laneId]: { eventPlacements, commandPlacements, viewPlacements,
                          automationPlacements, outboxPlacements,
                          triggerPlacements, syntheticPlacements } },
  laneColCount, totalCols,
  crossLaneAutomations: [{ auto, sourceLaneIdx, targetLaneIdx, fromCol, toCol }]
}
```

### `isRowVisibleInLane(laneData, rowKey)`

`buildLaneLayout` must track `eventCount` per lane (number of model events assigned to that lane).

```js
switch (rowKey) {
  case 'trigger':    return laneData.triggerPlacements.length > 0;
  case 'command':    return laneData.commandPlacements.length > 0
                         || laneData.syntheticPlacements.length > 0;
  case 'event':      return laneData.eventCount > 0;   // not unconditionally true
  case 'view':       return laneData.viewPlacements.length > 0;
  case 'automation': return laneData.automationPlacements.length > 0;
  case 'outbox':     return laneData.outboxPlacements.length > 0;
}
```

The `buildLaneLayout` return shape gains `eventCount` inside each `laneData` entry.

### Geometry helpers

```js
function laneContentHeight(laneData) {
  return ROWS.reduce((h, k) => isRowVisibleInLane(laneData, k) ? h + L.rowH[k] : h, 0);
}
function laneTotalHeight(laneData) {
  return L.laneHeaderH + laneContentHeight(laneData) + L.lanePadBottom;
}
function laneTopY(layout, laneIdx) {
  let y = L.titleBlockH;
  for (let i = 0; i < laneIdx; i++) y += laneTotalHeight(layout.laneData[layout.laneIds[i]]);
  return y;
}
function rowTopYInLane(layout, laneIdx, rowKey) {
  const laneData = layout.laneData[layout.laneIds[laneIdx]];
  let y = laneTopY(layout, laneIdx) + L.laneHeaderH;
  for (const k of ROWS) {
    if (k === rowKey) return y;
    if (isRowVisibleInLane(laneData, k)) y += L.rowH[k];
  }
  return y;
}
function totalHeightLanes(layout) {
  return layout.laneIds.reduce((h, id) => h + laneTotalHeight(layout.laneData[id]), L.titleBlockH) + 20;
}
```

## Rendering Changes

### `renderRows` — rewrite for lane path

For each lane (by index):
1. Band background rect from `laneTopY(layout, i)` to `laneTopY(layout, i) + laneTotalHeight(...)`,
   alternating fill `#fff8e8` / `#ece4d2`.
2. Lane header strip rect (`L.laneHeaderH` tall) with fill `#eadfc8` / `#ddd1b8` + stroke.
3. Centered lane label text in the header strip.
4. Top border line for the band.
5. For each visible row in the lane at `rowTopYInLane(layout, i, rowKey)`:
   - Dashed row separator line (full width).
   - Row label text (`TRIGGER` / `COMMAND` / …) and subtitle in the left gutter.
6. Bottom border line after the last visible row.

### `render` — branch for lane path

```js
const useLanes = (model.lanes || []).length > 0;
if (useLanes) {
  const layout = buildLaneLayout(model);
  // per-lane card rendering pass
  // cross-lane automation arc pass using renderArc
} else {
  // existing flat path unchanged
}
```

Per-lane card pass for each lane:
- Event cards, command cards, synthetics, triggers, views, outbox — same card functions as today,
  using `rowTopYInLane(layout, laneIdx, rowKey)` for Y and `defaultColX(col)` for X (X is shared
  across all lanes since all lanes start at `L.labelW`).
- Within-lane automations: same arcs as today.
- Cross-lane automations: draw automation card in source lane's automation row, then call
  `renderDownArc(srcCardCX, srcAutoCardBottom, tgtCmdCX, tgtCmdRowTop)`. Do **not** use
  `renderArc` here — its `my = max(y1,y2) + 30` overshoots below the target band.

Add a new function alongside the existing arrow helpers:

```js
function renderDownArc(x1, y1, x2, y2) {
  const cp1y = y1 + (y2 - y1) * 0.33;
  const cp2y = y1 + (y2 - y1) * 0.66;
  return el('path', {
    d: `M ${x1} ${y1} C ${x1} ${cp1y} ${x2} ${cp2y} ${x2} ${y2}`,
    stroke: C.arrow, 'stroke-width': '2', fill: 'none',
    'stroke-dasharray': '8 5', 'stroke-linecap': 'round',
    'marker-end': 'url(#arrowhead)',
  });
}
```

Control points at 1/3 and 2/3 of the vertical span keep the curve strictly between `y1` and `y2`.

## Acceptance

Open `http://localhost:8091/wallet.html`:
- [ ] Two horizontal bands stacked: "Wallet" on top, "Notification" below
- [ ] Wallet band rows (top to bottom): trigger, command, event, view, automation, outbox
- [ ] Notification band rows: command only (no other rows)
- [ ] Dashed bezier arc from WalletOpenedAutomation (Wallet automation row) to
      SendWelcomeNotification (Notification command row)
- [ ] No broken layout when `diagram.lanes` is present

Open `http://localhost:8091/course.html`:
- [ ] Two horizontal bands: "Course" on top, "Student enrollment" below
- [ ] Course band: trigger, command, event, view rows
- [ ] Student enrollment band: trigger, command, event rows
- [ ] No cross-lane arcs
- [ ] "multi-entity DCB" badge on StudentSubscribedToCourse
- [ ] No event-to-view arrows from StudentSubscribedToCourse to CourseAvailability
      (foreign-lane read — omit arrow, console warning only)

Flat model:
- [ ] A model with no `diagram.lanes` renders identically to the current flat layout
