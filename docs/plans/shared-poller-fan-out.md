# Plan: Within-Module Batch Fetch + In-Memory Fan-Out (v6 — approved)

v6 tightens cross-references (scheduler modes, NOTIFY, shared vs legacy path) and records revision history at the end.

## Context

Today, N views = N separate DB queries per wakeup cycle. Same for automations and outbox. Each `EventProcessorImpl` schedules one task per processor ID (`doInitializeSchedulers`); `requestImmediatePoll` iterates every enabled processor on NOTIFY — so N views produce N concurrent read queries per wakeup.

The fix: keep module-level isolation (views, automations, outbox stay separate) but within each module execute **one query per cycle** and route events in-memory to the right processor.

## Module isolation — unchanged

```
crablet-views        → own EventProcessor (own leader lock, own scheduler)
crablet-automations  → own EventProcessor (own leader lock, own scheduler)
crablet-outbox       → own EventProcessor (own leader lock, own scheduler)
```

Scheduler mode depends on the feature flag:
- **shared-fetch disabled (default):** one scheduled task per processor — existing behaviour unchanged
- **shared-fetch enabled:** one shared module scheduler; N processors are served by that single task

## Three-cursor model

```
moduleScanCursor             per module     how far the module has fetched from the DB
processor.scannedPosition    per processor  last event position the processor has considered
processor.handledPosition    per processor  last matching event successfully processed
                                            (= current lastPosition, public API unchanged)
```

### Update rules per cycle

`moduleScanCursor` always advances to window end after every fetch. It never stalls.

| Case | `handledPosition` | `scannedPosition` | Next processor state |
|---|---|---|---|
| No matching events in window | unchanged | → window end | — |
| Matches ≤ batchSize, handler OK | → last matched position | → **window end** | — |
| Matches > batchSize, dispatched batch OK | → last dispatched matched position | → last dispatched matched position | → `CATCHING_UP` |
| Handler failed | unchanged | unchanged | → `CATCHING_UP` (or `FAILED` if maxErrors) |
| PAUSED / FAILED / CATCHING_UP (excluded) | unchanged | unchanged | — |

**Why `scannedPosition → window end` on full success:** if the module fetched positions 1–1000 and a processor matched only event 200, after successfully handling 200 the processor has already evaluated 201–1000 and found nothing. Its `scannedPosition` must become 1000 (window end), not 200 — otherwise it looks artificially behind and triggers unnecessary catch-up.

**Why `scannedPosition → last dispatched` on partial dispatch:** if the window contains 600 matching events and `processor.batchSize = 100`, the processor has only safely considered through the 100th matched event. It has not evaluated the rest. Set `scannedPosition` to the last dispatched matched position and mark `CATCHING_UP` to handle the remainder.

## v1 fetch: position-only (no union filter in SQL)

```sql
SELECT type, tags, data, transaction_id, position, occurred_at, correlation_id, causation_id
FROM events
WHERE position > ?
ORDER BY position ASC
LIMIT ?
```

No tag/type filter in SQL. `EventSelectionMatcher` handles all routing in-memory after the fetch.

**Why position-only for v1:**
- `moduleScanCursor` is a true scan cursor over the event log, not a "deliverable active-selection cursor." Those are different things.
- Union-filtered SQL only sees events active processors care about right now. If all processors are PAUSED or events only match a FAILED processor, the union returns zero rows and the module cursor stalls — contradicting the guarantee that it always advances.
- Position-only fetch eliminates the stall risk entirely. The tradeoff is reading irrelevant events; that is acceptable for a correct v1.
- Very predictable query plan: primary key / position index, no GIN scan, no complex OR predicate.

**v2 optimization (deferred):** union-filtered fetch as an optional mode after v1 cursor semantics are proven correct and EXPLAIN validated at scale.

## Isolation and durability rules

### Handler exception isolation

Handler exceptions are isolated per processor. A failed processor does not abort dispatch for other processors in the same module cycle. The module cycle catches each processor's exception independently, marks that processor appropriately, then continues with the remaining processors and still advances `moduleScanCursor` to window end.

Letting one handler exception propagate out and abort the whole module run would recreate head-of-line blocking — exactly the problem the design is solving.

### Cursor update durability order

For a processor with matched events, the persistence order is:

1. Run handler.
2. Update `handledPosition` only after handler success.
3. Update `scannedPosition` only after the `handledPosition`/scanned decision is determined.
4. Advance `moduleScanCursor` after the full window has been considered by all processors.

If `moduleScanCursor` advances but a processor cursor update fails, recovery is safe — the processor catches up from its older `scannedPosition`. If `handledPosition` update fails after a successful handler, existing at-least-once semantics already permit reprocessing.

## New flow (per module, shared-fetch enabled)

When shared-fetch is **disabled**, the existing per-processor `EventProcessorImpl` loop applies — this section describes only the opt-in path.

```
Wakeup → 1 scheduled task fires per module
  1. Load moduleScanCursor
  2. Fetch: SELECT WHERE position > moduleScanCursor ORDER BY position ASC LIMIT fetchBatchSize
  3. If zero rows fetched: record module-level empty (backoff eligible), return
  4. For each fetched event: match in-memory against each ACTIVE, non-catching-up processor
     using EventSelectionMatcher
  5. For each processor:
       a. Matches ≤ batchSize AND handler OK:
            update handledPosition to last matched position
            update scannedPosition to window end
       b. Matches > batchSize AND dispatched batch OK:
            update handledPosition to last dispatched matched position
            update scannedPosition to last dispatched matched position
            mark processor as CATCHING_UP for remainder
       c. Handler failed:
            record error; do not advance handledPosition or scannedPosition
            mark processor as CATCHING_UP (or FAILED if maxErrors)
       d. No matching events:
            advance scannedPosition to window end (last fetched position)
  6. Advance moduleScanCursor to last fetched event position
```

**"Empty" for backoff = zero rows fetched from DB.** Fetching rows that no active processor matched still counts as useful work (scan cursor advanced) and does not trigger backoff.

## Processor internal states

Public `ProcessorStatus` (ACTIVE, PAUSED, FAILED) is unchanged.

Internally add `CATCHING_UP` — not exposed publicly, tracked in a module-level set:

```
ACTIVE           → participates in shared fan-out
PAUSED           → excluded from shared fan-out; scannedPosition frozen until resume
FAILED           → excluded from shared fan-out; scannedPosition frozen until resume
CATCHING_UP      → excluded from shared fan-out; runs own bounded catch-up loop
```

A processor transitions to `CATCHING_UP` when its `scannedPosition < moduleScanCursor` (on resume or after a handler failure that doesn't yet hit `maxErrors`).

## Module scheduler semantics

**Polling interval:** `min(pollingIntervalMs across all enabled processor configs)` — preserves the fastest processor's latency expectation. Since shared mode is opt-in, this is acceptable.

**Backoff:** module-level only. Empty = zero DB rows fetched. Fetched rows with zero dispatches = not empty (do not increment backoff, do not reset it either).

**NOTIFY wakeup:** with shared-fetch enabled, one wakeup schedules **one** immediate module run (not N processor runs). With shared-fetch disabled, behaviour is unchanged: `requestImmediatePoll` still schedules each enabled processor at delay 0.

**Concurrency guard:** one shared cycle or catch-up orchestration at a time per module. Use a module-level run slot (similar to today's `runningProcessors` map) so catch-up and shared dispatch cannot run concurrently for the same module.

## Resume and catch-up path

On `resume(processorId)`:
1. Set public status to ACTIVE.
2. If `scannedPosition < moduleScanCursor`: mark processor as `CATCHING_UP`.
3. Run bounded catch-up loop using a new **internal** method:
   ```
   fetchEventsUntil(processorId, afterPosition=scannedPosition,
                    upToPosition=moduleScanCursor, batchSize=processor.batchSize)
   ```
   SQL: `WHERE position > afterPosition AND position <= upToPosition AND <processor selection> ORDER BY position ASC LIMIT batchSize`
4. Per iteration:
   - **Rows found:** handle batch; advance `handledPosition` and `scannedPosition` to last handled row; repeat.
   - **Zero rows found (sparse — no matching events before `moduleScanCursor`):** advance `scannedPosition` directly to `moduleScanCursor`; exit catch-up. This mirrors the "no matching events in window" rule from the shared loop — sparse processors must not stay in `CATCHING_UP` forever.
5. Once `scannedPosition >= moduleScanCursor`: remove from `CATCHING_UP` set, processor rejoins shared fan-out.

**Why bounded and not `eventFetcher.fetchEvents` as-is:** the existing `fetchEvents` has no upper bound. If `moduleScanCursor = 1000`, `scannedPosition = 500`, and the next matching event is at position 1500, unbounded catch-up would process position 1500 before the shared module cursor reaches it — breaking the model. The bounded method stays internal and does not change the public `EventFetcher` interface.

Catch-up loop is guarded by the module-level run slot. Does not disturb `moduleScanCursor`.

## In-memory event matching: `EventSelectionMatcher`

New class in `crablet-event-poller`:

```java
EventSelectionMatcher.matches(EventSelection selection, StoredEvent event) → boolean
```

Mirrors `EventSelectionSqlBuilder` exactly:
- empty event types = match all; otherwise type must be in set
- required tag keys: all must be present as `key=*` in tags array
- any-of tag keys: at least one must be present
- exact tag key=value pairs: must match exactly

Unit-tested for parity with SQL builder across all filter combinations. Tag key/value inputs are domain-controlled (same trust boundary as `EventSelectionSqlBuilder`).

## Schema

New tables. **Not** owned by the poller jar (no Flyway in `crablet-event-poller` main scope today).

**Required only when shared-fetch is enabled.** Flyway always runs migrations that are present — it does not conditionally skip them. What the feature flag controls is **runtime access**: when `crablet.views.shared-fetch.enabled=false` (the default), the shared-fetch code paths must not query the new tables at all. Existing apps that have not added the new migrations will not have the tables, but that is safe because the runtime code never touches them when disabled. Apps must not fail startup just because the new classpath code exists.

**Migration added to:**
- `wallet-example-app` (Flyway migration)
- `crablet-test-support` (shared test migration)

**Documented in:** `crablet-event-poller/README.md` and a new `crablet-event-poller/SCHEMA.md` listing required tables for shared-fetch mode.

```sql
CREATE TABLE crablet_module_scan_progress (
    module_name   TEXT PRIMARY KEY,
    scan_position BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE crablet_processor_scan_progress (
    module_name       TEXT   NOT NULL,
    processor_id      TEXT   NOT NULL,
    scanned_position  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (module_name, processor_id)
);
```

**Outbox `processor_id` serialization:** use `topic + ":" + publisherName` as the stable string key (e.g. `"orders:kafka"`). This is already the implicit `toString()` convention on `TopicPublisherPair`. Formalize it as a `toKey()` method on `TopicPublisherPair` to prevent drift if `toString()` changes.

`handledPosition` stays in each module's existing progress table — no change to existing schema.

## Batch size

```properties
crablet.views.fetch-batch-size=1000
crablet.automations.fetch-batch-size=1000
crablet.outbox.fetch-batch-size=1000
```

Default: 1000. Per-processor `batchSize` still caps matched-event dispatch per processor per call.

## Feature flag

```properties
crablet.views.shared-fetch.enabled=true   # default: false
```

Old per-processor scheduler behavior is fully preserved when disabled. Backoff granularity change (per-processor → per-module) only affects apps that opt in.

**Enabling the flag requires the scan progress migrations to be applied first.** Enabling it before the migrations exist will cause startup or first-poll failure when the code attempts to read `crablet_module_scan_progress` / `crablet_processor_scan_progress`. Apply the Flyway migrations, verify, then set the flag.

**When disabled, no shared-fetch beans are constructed or validated.** The disabled path must behave exactly like today's `EventProcessorImpl` — no eager initialization of scan-progress repositories, no table access, no new scheduled tasks. Spring `@ConditionalOnProperty` on all shared-fetch-specific beans is the safe wiring approach.

## Management metrics (correct definitions)

- `scanLag = moduleScanCursor - scannedPosition` — real catch-up lag; 0 for healthy sparse processors
- `lastHandledPosition = handledPosition` — last matching event successfully processed
- **Do not expose `moduleScanCursor - handledPosition`** — misleading for sparse processors

New management status fields (additive, backward compatible):
- `scannedPosition`
- `scanLag`
- `catchingUp` (boolean)

### Operational visibility metrics (new per module cycle)

| Metric | Purpose |
|---|---|
| `events.module.fetched` | Events fetched from DB per cycle — baseline for read cost |
| `events.module.matched` | Events matched to at least one processor — signal/noise ratio |
| `events.module.dispatched` | Total events dispatched across all processors — actual work done |
| `events.processor.catching_up_count` | Processors currently in CATCHING_UP state |

Position-only v1 can read many irrelevant events in high-volume streams. The `fetched / matched / dispatched` ratio makes the cost visible and determines whether v2 union-filter optimization is needed in practice. Make `fetch-batch-size` easy to tune independently so operators can adjust read cost without touching individual processor configs.

## Rollout order

1. **`EventSelectionMatcher`** — standalone unit tests, no Spring wiring
2. **Cursor state machine** as internal classes, unit-tested independently from scheduling
3. **Schema** — migrations in `wallet-example-app` + `crablet-test-support`; document in poller README
4. **Shared fetch loop for views only** behind `crablet.views.shared-fetch.enabled=true`
5. **Integration tests:**
   - position-only fetch: module cursor always advances
   - sparse filter (zero matches): `scanLag = 0`, no fake lag
   - PAUSED processor: module cursor advances, processor stays put
   - FAILED processor: same
   - Resume after pause: catch-up from `scannedPosition`
   - Handler failure → CATCHING_UP → resume
   - Disjoint filters (no overlap between processors)
   - Per-processor `batchSize` respected during dispatch
   - **Handler isolation:** processor A throws during dispatch; processor B in same cycle succeeds; `moduleScanCursor` still advances to window end; A is in `CATCHING_UP` or `FAILED` as appropriate
6. Port automations
7. Port outbox

## What NOT to change

- Public `EventProcessor`, `EventHandler`, `EventFetcher`, `EventSelection`, `ProcessorStatus` (ACTIVE/PAUSED/FAILED) interfaces
- Existing `lastPosition` semantics in public management API
- Existing module progress tables (`view_progress`, `automation_progress`, `outbox_topic_progress`)
- Leader election
- LISTEN/NOTIFY wakeup
- Per-module configuration properties (add, don't replace)

## Revision notes

- **v5:** Position-only fetch, three-cursor model, internal `CATCHING_UP`, bounded catch-up, schema in app + test-support, operational metrics, handler isolation rules, migration-before-enable and conditional beans, handler-isolation integration test in rollout.
- **v6:** Version bump; "New flow" explicitly scoped to shared-fetch with pointer to legacy path when disabled; NOTIFY behaviour spelled out for shared-fetch on vs off; this revision log.
