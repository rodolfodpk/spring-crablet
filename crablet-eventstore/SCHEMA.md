# Database Schema

Crablet adds three framework Flyway migrations to your PostgreSQL database — nothing else.

| Migration | Owns | Tables |
|---|---|---|
| `V1__crablet_eventstore_schema.sql` | Core event store | `crablet_events`, `crablet_event_tags` + 2 PL/pgSQL functions |
| `V2__crablet_commands_schema.sql` | Command audit | `crablet_commands` |
| `V3__crablet_processing_schema.sql` | Processing progress | `crablet_outbox_topic_progress`, `crablet_view_progress`, `crablet_automation_progress`, shared-fetch progress tables |

---

## V1 — Event Store

```sql
CREATE TABLE crablet_events
(
    type           TEXT                     NOT NULL,
    tags           TEXT[]                   NOT NULL,
    data           JSONB                    NOT NULL,
    transaction_id xid8                     NOT NULL,
    position       BIGSERIAL                NOT NULL PRIMARY KEY,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id UUID,
    causation_id   BIGINT,
    CONSTRAINT chk_event_type_length CHECK (LENGTH(type) BETWEEN 1 AND 64)
);
```

**Why `tags TEXT[]`?** Tags are stored as `key=value` strings in a Postgres array (`{"wallet_id=abc", "deposit_id=xyz"}`). A GIN index makes containment queries (`tags @> ARRAY['wallet_id=abc']`) fast without joins. This is the same array that drives DCB conflict detection — the decision model query is just a GIN lookup.

**Why `xid8`?** `transaction_id` is PostgreSQL's internal transaction ID for the transaction that appended the event. It provides a safe ordering guarantee: the `append_events_if` function checks events whose `transaction_id < pg_snapshot_xmin(pg_current_snapshot())`, which excludes events from in-flight transactions — no dirty reads possible. It also links events to command audit rows without a foreign key.

**`crablet_event_tags` is derived, not canonical.** The `crablet_events.tags` array is the source of truth. `crablet_event_tags` is a key/value lookup table maintained atomically on every append, giving the poller SQL an indexed `(key, value, position)` shape instead of scanning `unnest(tags)` per row. Reading or writing tags directly should always go through `crablet_events.tags`.

### The two append functions

**`append_events_batch()`** — simple insert, no conditions. Used for commutative events where order doesn't matter and no DCB check is needed. Supports an application-controlled `occurred_at` timestamp for deterministic testing.

**`append_events_if()`** — the heart of DCB. Atomically checks for conflicts and duplicates, then appends. Returns JSONB so the caller distinguishes between success, an idempotency violation, and a DCB conflict:

```json
{ "success": true,  "error_code": null }
{ "success": false, "error_code": "IDEMPOTENCY_VIOLATION" }
{ "success": false, "error_code": "DCB_VIOLATION" }
```

**DCB conflict check** queries events after a cursor position using MVCC snapshot isolation — no locking needed. If transaction A appended at position 43, transaction B will see it through the snapshot and detect the conflict naturally.

**Idempotency check** is different: there is no prior cursor position to anchor the check. Without a lock, two concurrent transactions can both query "has this command already run?" and both see "no" before either commits — producing a duplicate. `append_events_if` serializes idempotency checks with `pg_advisory_xact_lock()`, scoped to a hash of the idempotency key. The lock is held only for the duration of the check-and-insert, and released automatically at transaction end.

### Tags in Java

```java
AppendEvent.builder("WalletOpened")
    .tag("wallet_id", "wallet-123")   // stored as "wallet_id=wallet-123"
    .tag("owner_id",  "user-456")     // stored as "owner_id=user-456"
    .build();
```

Querying directly in SQL:

```sql
-- exact match (uses GIN index)
SELECT * FROM crablet_events WHERE tags @> ARRAY['wallet_id=wallet-123'];

-- all events for a wallet, any type
SELECT * FROM crablet_events WHERE tags @> ARRAY['wallet_id=wallet-123'] ORDER BY position;
```

---

## Command Audit

```sql
CREATE TABLE crablet_commands
(
    command_id     UUID                     NOT NULL PRIMARY KEY,
    transaction_id xid8                     NOT NULL UNIQUE,
    type           TEXT                     NOT NULL,
    data           JSONB                    NOT NULL,
    metadata       JSONB,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_crablet_command_type_length CHECK (LENGTH(type) BETWEEN 1 AND 64)
);
```

`command_id` is the command identity and idempotency key. `transaction_id` is the join key to `crablet_events.transaction_id`: both are written in the same Postgres transaction, so they share the same `pg_current_xact_id()` value.

**Why no foreign key?** The linkage is via `xid8`, not a referential constraint. Postgres does not support FK constraints on `xid8` columns. The uniqueness constraint on `crablet_commands.transaction_id` ensures one command maps unambiguously to the events it produced. The join is always `crablet_commands.transaction_id = crablet_events.transaction_id`.

`transaction_id` is not a business concept — it is not a deposit ID, withdrawal ID, or order ID. Those belong in `tags`.

---

## Poller Progress

Five tables that track cursor positions and leader election state for the polling infrastructure:

| Table | Used by |
|---|---|
| `crablet_view_progress` | Views — one row per named view projector |
| `crablet_automation_progress` | Automations — one row per named automation |
| `crablet_outbox_topic_progress` | Outbox — one row per topic/publisher pair |
| `crablet_module_scan_progress` | Shared-fetch — one row per module |
| `crablet_processor_scan_progress` | Shared-fetch — one row per processor within a module |

Each table tracks `last_position` (the highest event position processed), `status` (`ACTIVE`, `PAUSED`, `FAILED`), and leader election columns (`leader_instance`, `leader_heartbeat`).

Identifier lengths are enforced as `CHECK` constraints rather than `VARCHAR(n)`: types at 64 chars, topics/publishers at 128, view/automation names at 256, instance IDs at 256, module names at 64, processor IDs at 320 (accommodating outbox's `topic:publisher` composite key).

---

## Full DDL

The authoritative DDL is in the migration files — not duplicated here:

- [`V1__crablet_eventstore_schema.sql`](../crablet-db-migrations/src/main/resources/db/migration/V1__crablet_eventstore_schema.sql)
