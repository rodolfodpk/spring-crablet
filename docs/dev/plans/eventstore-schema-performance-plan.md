# EventStore Schema Performance Plan

> **Status: Partially implemented** — V7 (schema + FK + backfill) and V8 (append maintenance)
> landed on `feat/event-tags-derived-index`. V9 is a no-op: the idempotency/DCB query path
> switch was evaluated and intentionally not implemented — real decision models use 2+ tags per
> criterion (e.g. `wallet_id + year + month`), so the GIN path on `events.tags` handles the
> common case directly; a single-tag B-tree fast path would add write amplification for all
> appends while benefiting only a minority of commands. `event_tags` is consumed exclusively
> by the per-processor poller (`EventSelectionSqlBuilder`).
> Deferred items (shared-fetch indexed_selection, consistency boundaries) remain open.

## Context

Crablet currently uses a compact event store schema:

- `events` is the canonical event log.
- `events.tags` stores tags inline as `TEXT[]`.
- `commands` stores command audit records keyed by `transaction_id`.
- `append_events_if` performs idempotency and DCB conflict checks against `events`.
- The event poller either fetches filtered events per processor or uses shared-fetch to scan
  by `position` and route in memory.

Two concrete performance problems exist today:

1. `EventSelectionSqlBuilder` uses `EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE 'key=%')`
   for required-tag-key and any-of-tag filters. This forces a per-row array expansion and cannot
   use the GIN index on `events.tags`, making it expensive at scale.

2. Idempotency checks and DCB conflict checks inside `append_events_if` use `events.tags @> ?`,
   which requires a GIN scan over the full events table for each append.

Both problems are solved by a derived `event_tags` table — a normalized, B-tree-indexed
representation of tags maintained atomically with every append.

## Goals

- Fix per-processor poller tag filtering without touching shared-fetch semantics.
- Improve idempotency and DCB conflict check query plans.
- Keep `events` as the canonical source of truth; `event_tags` is derived data.
- Deliver the improvement in three independently deployable steps.
- Keep schema and operational behavior understandable.

## Non-Goals

- Replace `events.tags TEXT[]`.
- Introduce Axon-style global index finalization.
- Add a `consistency_boundaries` table (deferred; see below).
- Add a runtime feature flag for `event_tags` — the migration is the opt-out.
- Command-level idempotency (separate initiative; see below).
- Shared-fetch `indexed_selection` strategy (deferred; see below).

## Delivery Scope

Three steps, each independently deployable and independently reversible:

1. **Add `event_tags` table and backfill.** No query changes. Zero behavioral risk.
2. **Maintain `event_tags` inside `append_events_batch`.** No read-path changes. Append
   latency impact is measurable in isolation here.
3. **Switch per-processor poller and idempotency check queries to `event_tags`.**
   Gated on passing drift tests.

---

## Step 1 — Schema and Backfill

### Table definition

```sql
CREATE TABLE event_tags (
    position       BIGINT NOT NULL,
    key            TEXT   NOT NULL,
    value          TEXT   NOT NULL,
    PRIMARY KEY (key, value, position)
);

CREATE INDEX idx_event_tags_position
    ON event_tags (position);
```

Primary key `(key, value, position)` clusters the table for key/value lookups.
Event type and transaction visibility remain available from `events` through `position`.

### Backfill

```sql
INSERT INTO event_tags (position, key, value)
SELECT
    e.position,
    split_part(tag, '=', 1)                              AS key,
    substring(tag FROM position('=' IN tag) + 1)         AS value
FROM events e,
LATERAL unnest(e.tags) AS tag
WHERE tag LIKE '%=%'
ON CONFLICT DO NOTHING;
```

`ON CONFLICT DO NOTHING` makes the backfill idempotent — safe to re-run after interruption.
`WHERE tag LIKE '%=%'` skips any tag stored without a key/value separator (defensive only;
the current encoder always produces `key=value` strings).

### Drift check

Run after backfill to confirm completeness before proceeding to step 2:

```sql
SELECT e.position
FROM events e
WHERE cardinality(e.tags) > 0
  AND NOT EXISTS (
      SELECT 1 FROM event_tags t WHERE t.position = e.position
  )
LIMIT 10;
```

Zero rows means the backfill is complete. This query should return no rows before step 3
query paths are enabled.

### Tag encoding

Tags are stored in `events.tags` as `TEXT[]` where each element is `key=value`. The first
`=` is the separator; values may contain `=`. `split_part(tag, '=', 1)` extracts the key
correctly. `substring(tag FROM position('=' IN tag) + 1)` extracts everything after the
first `=`, preserving any `=` characters in the value.

This matches `EventStoreImpl.parseTags`, which uses the same first-`=` rule.

---

## Step 2 — Append Maintenance

Modify `append_events_batch` to maintain `event_tags` atomically in the same transaction
using a CTE. The function signature does not change.

```sql
CREATE OR REPLACE FUNCTION append_events_batch(
    p_types      TEXT[],
    p_tags       TEXT[],
    p_data       JSONB[],
    p_occurred_at TIMESTAMP WITH TIME ZONE
) RETURNS VOID AS
$$
BEGIN
    WITH inserted AS (
        INSERT INTO events (type, tags, data, transaction_id, occurred_at)
        SELECT
            t.type,
            t.tag_string::TEXT[],
            t.data,
            pg_current_xact_id(),
            p_occurred_at
        FROM UNNEST($1, $2, $3) AS t(type, tag_string, data)
        RETURNING position, tags
    )
    INSERT INTO event_tags (position, key, value)
    SELECT
        i.position,
        split_part(tag, '=', 1),
        substring(tag FROM position('=' IN tag) + 1)
    FROM inserted i,
    LATERAL unnest(i.tags) AS tag
    WHERE tag LIKE '%=%';
END;
$$ LANGUAGE plpgsql;
```

### What this changes

- Every append now writes `sum(tags per event)` additional rows to `event_tags`.
- `events` remains the canonical write; `event_tags` rows are derived in the same CTE,
  same transaction, same visibility boundary.
- Both call paths — `APPEND_EVENTS_IF_SQL` (standalone) and `APPEND_EVENTS_IF_CONNECTION_SQL`
  (connection-scoped, used by `executeInTransaction`) — route through `append_events_batch`,
  so both are covered without additional changes.

### Tradeoffs at this step

- Write amplification is real. Events with many tags (e.g. tenant + entity type + entity id
  + correlation scope) produce 4× or more rows per event. Append latency will increase.
- The latency increase is measurable here in isolation, before any read-path changes.
  Take the two benchmark measurements (see Benchmarks section) before and after this step.
- If the write cost is unacceptable at this point, stop here and reassess scope. No read
  paths have changed, so rollback is a schema drop and function restore.

---

## Step 3 — Query Path Switch

Gated on: drift check from step 1 passes, and step 2 has been running in production
long enough to confirm `event_tags` is current for all new appends.

### 3a — Per-processor poller (`EventSelectionSqlBuilder`)

Current implementation uses `unnest(tags) LIKE` patterns, which cannot use the GIN index:

```java
// required tag key (expensive today)
conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE t LIKE '" + tagKey + "=%')");

// any-of tags (expensive today)
conditions.add("EXISTS (SELECT 1 FROM unnest(tags) AS t WHERE " + anyOfCondition + ")");

// exact tag (fine today, uses ANY(tags))
conditions.add("'" + entry.getKey() + "=" + entry.getValue() + "' = ANY(tags)");
```

Replace the `unnest`-based filters with `event_tags` joins. Query shape for each case:

**Required tag key** — event must have at least one tag with this key, any value:

```sql
SELECT e.*
FROM event_tags t
JOIN events e ON e.position = t.position
WHERE t.position > ?
  AND t.key = ?
ORDER BY t.position ASC
LIMIT ?;
```

**Any-of tags** — event must have at least one tag with any of these keys:

```sql
SELECT DISTINCT ON (e.position) e.*
FROM event_tags t
JOIN events e ON e.position = t.position
WHERE t.position > ?
  AND t.key = ANY(?)
ORDER BY e.position ASC
LIMIT ?;
```

`DISTINCT ON (e.position)` prevents duplicate event rows when an event matches multiple keys.

**Exact tag** — event must have a specific `key=value` pair:

```sql
SELECT e.*
FROM event_tags t
JOIN events e ON e.position = t.position
WHERE t.position > ?
  AND t.key = ?
  AND t.value = ?
ORDER BY t.position ASC
LIMIT ?;
```

**Multiple selection clauses** — combine with subquery over candidate positions:

```sql
SELECT e.*
FROM events e
WHERE e.position IN (
    SELECT t.position
    FROM event_tags t
    WHERE t.position > ?
      AND t.key = ?      -- repeat per clause
      AND t.value = ?
    -- add additional EXISTS or IN subqueries per clause
)
ORDER BY e.position ASC
LIMIT ?;
```

For the initial implementation, cover the three single-clause cases. Multi-clause
combinations can be added incrementally.

### 3b — Idempotency check inside `append_events_if`

Current check (inside the existing `pg_advisory_xact_lock` block):

```sql
EXISTS (
    SELECT 1 FROM events e
    WHERE e.type = ANY(p_idempotency_types)
      AND e.tags @> p_idempotency_tags
    LIMIT 1
)
```

Replace with `event_tags` for the common single-tag case. The advisory lock block is
unchanged — do not remove it. The lock prevents TOCTOU races; only the query target changes.

**Single tag pair** (covers the vast majority of `appendIdempotent` call sites):

```sql
EXISTS (
    SELECT 1
    FROM event_tags t
    JOIN events e ON e.position = t.position
    WHERE e.type = ANY(p_idempotency_types)
      AND t.key   = split_part(p_idempotency_tags[1], '=', 1)
      AND t.value = substring(p_idempotency_tags[1] FROM position('=' IN p_idempotency_tags[1]) + 1)
    LIMIT 1
)
```

**Multi-tag case** — keep the existing `events.tags @>` check as a fallback when
`array_length(p_idempotency_tags, 1) > 1`. This avoids the GROUP BY/HAVING complexity
and keeps the common path fast.

```sql
CASE
    WHEN array_length(p_idempotency_tags, 1) = 1 THEN
        EXISTS (
            SELECT 1
            FROM event_tags t
            JOIN events e ON e.position = t.position
            WHERE e.type  = ANY(p_idempotency_types)
              AND t.key   = split_part(p_idempotency_tags[1], '=', 1)
              AND t.value = substring(p_idempotency_tags[1]
                              FROM position('=' IN p_idempotency_tags[1]) + 1)
            LIMIT 1
        )
    ELSE
        EXISTS (
            SELECT 1 FROM events e
            WHERE e.type = ANY(p_idempotency_types)
              AND e.tags @> p_idempotency_tags
            LIMIT 1
        )
END
```

### 3c — DCB conflict check inside `append_events_if`

Current check:

```sql
EXISTS (
    SELECT 1 FROM events e
    WHERE (p_event_types IS NULL OR e.type = ANY(p_event_types))
      AND (p_condition_tags IS NULL OR e.tags @> p_condition_tags)
      AND (p_after_cursor_position IS NULL OR e.position > p_after_cursor_position)
      AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
    LIMIT 1
)
```

Replace with `event_tags` for the single-tag-pair case (same CASE pattern as idempotency).
The snapshot visibility filter `e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())`
must be preserved. Join `event_tags` back to `events` to apply it:

```sql
EXISTS (
    SELECT 1
    FROM event_tags t
    JOIN events e ON e.position = t.position
    WHERE t.position > p_after_cursor_position
      AND e.type  = ANY(p_event_types)
      AND t.key   = split_part(p_condition_tags[1], '=', 1)
      AND t.value = substring(p_condition_tags[1]
                      FROM position('=' IN p_condition_tags[1]) + 1)
      AND e.transaction_id < pg_snapshot_xmin(pg_current_snapshot())
    LIMIT 1
)
```

Fall back to the original `events.tags @>` check when `p_condition_tags` has more than
one element.

### 3d — Drift tests

Add automated drift checks before enabling any query in steps 3a–3c. Run these in the
integration test suite against a populated database:

```sql
-- No events with tags should be missing from event_tags
SELECT e.position
FROM events e
WHERE cardinality(e.tags) > 0
  AND NOT EXISTS (
      SELECT 1 FROM event_tags t WHERE t.position = e.position
  )
LIMIT 1;

-- No event_tags row should reference a non-existent event
SELECT t.position
FROM event_tags t
WHERE NOT EXISTS (
    SELECT 1 FROM events e WHERE e.position = t.position
)
LIMIT 1;

-- Tag count per position must match
SELECT e.position
FROM events e
JOIN (SELECT position, COUNT(*) AS tag_count FROM event_tags GROUP BY position) t
    ON t.position = e.position
WHERE t.tag_count != cardinality(e.tags)
LIMIT 1;
```

All three queries must return zero rows before step 3 query paths are enabled.

---

## Benchmarks

Two measurements, taken before and after step 2, and again after step 3:

**Measurement 1 — Idempotency check latency under concurrent load**

Scenario: 20 concurrent writers, same idempotency tag key/value, against a table with
500k events. Measure P50 and P99 of `append_events_if` call duration.

Expected outcome: step 3 reduces P99 compared to step 2 baseline. Step 2 may increase
P50 slightly due to write amplification.

**Measurement 2 — Per-processor poller fetch time against sparse tag filter**

Scenario: single processor with a required-tag-key filter, event table with 500k events
of which 5% match. Measure time per fetch batch.

Expected outcome: step 3 fetch time is proportional to matching events, not total events.

These are decision inputs, not acceptance gates. If measurement 1 shows step 3 made
idempotency checks faster but step 2 added 30% to append latency at that event volume,
that is a real signal to reassess scope — not a reason to continue regardless.

---

## Deferred: Command-Level Idempotency

Command-level idempotency (pre-handler short-circuit on duplicate command key) is a
separate initiative. It requires:

- New public API surface: an `IdempotentCommand` interface or `CommandIdempotencyKeyResolver`.
- A separate `command_idempotency` table (Option B), not an extension to `commands`.
  The `commands` row is written after the append, keyed on `transaction_id` — there is no
  `transaction_id` available before handler execution, so Option A cannot support
  pre-handler detection.
- Changes to the `CommandExecutor` transaction flow.
- A TTL/expiry policy for `command_idempotency` rows.

This has no dependency on `event_tags` and should be designed independently when the
`event_tags` work is stable.

---

## Deferred: Shared-Fetch `indexed_selection` Strategy

Shared-fetch is designed for broad subscriptions (many processors, most events match).
Per-processor polling with `event_tags` covers sparse subscriptions (few matching events
per fetch). The overlap — sparse processors using shared-fetch — is not a measured
bottleneck.

Adding an `indexed_selection` fetch strategy to `SharedFetchModuleProcessor` requires
redesigning the module scan cursor semantics. The current cursor advances by
position-contiguous windows. An indexed selection that returns sparse positions breaks
the `ProcessorCursorStateMachine`'s catching-up logic and the gap between
`scannedPosition` and `moduleScanCursor` becomes undefined.

Defer until there is a concrete complaint from a production deployment and a measured
baseline to improve against.

---

## Deferred: Consistency Boundaries

A future `consistency_boundaries` table could make non-commutative conflict checks
into compact boundary lookups instead of event scans. Risks:

- Wrong boundary derivation allows invalid concurrent writes.
- Hot boundaries introduce row-level contention.
- Every append must update all affected boundaries atomically.

Only revisit after `event_tags` is stable and benchmarks show `appendNonCommutative`
conflict checks remain a bottleneck at production event volumes.

---

## Rollout Sequence

1. Add benchmarks: idempotency check under concurrent load, poller fetch with sparse filter.
2. Add `event_tags` schema (step 1). Run backfill. Run drift check. Deploy. No behavior change.
3. Modify `append_events_batch` (step 2). Deploy. Measure append latency against step 1 baseline.
4. Add drift tests to integration suite.
5. Switch per-processor poller queries (step 3a). Deploy. Measure poller fetch against step 1 baseline.
6. Switch idempotency check (step 3b). Deploy.
7. Switch DCB conflict check (step 3c). Deploy.
8. Remove fallback `events.tags @>` paths once step 3b/3c have been stable in production.
