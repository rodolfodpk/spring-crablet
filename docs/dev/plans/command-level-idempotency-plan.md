# Command-Level Idempotency Plan

## Context

Crablet has two idempotency mechanisms today:

- **Event-level idempotency** — `appendIdempotent` checks whether output events with a given
  type and tag already exist before appending. Protected by an advisory lock to prevent TOCTOU
  races. The lock serializes all concurrent writers sharing the same idempotency tag hash,
  creating a bottleneck under retry storms.

- **Command-level idempotency** (this plan) — a pre-handler check that detects whether a
  command has already been successfully processed, before the handler runs. Uses a
  `command_idempotency` table with a `PRIMARY KEY` constraint. PostgreSQL's unique index
  handling serializes per-key without a global advisory lock, and releases automatically on
  rollback.

The two mechanisms are complementary. Command-level idempotency eliminates the advisory lock
from the command-mediated path and avoids executing the handler entirely on duplicates.
Event-level idempotency remains for direct `EventStore.appendIdempotent` calls and as
defense-in-depth for `CommandDecision` variants that carry an `IdempotencyKey`.

### Advisory lock analysis

The advisory lock in `append_events_if` cannot be removed for event-level idempotency because
the check is a read-then-insert against existing events: there is no unique key to enforce
with a constraint. The lock is the correct mechanism for that path and stays.

For command-mediated writes, command-level idempotency replaces the advisory lock path
entirely — the executor detects the duplicate before reaching `append_events_if`.

**Concurrency:** Advisory lock serializes all writers with the same tag hash. Command-level
UNIQUE constraint serializes only per command key, with no hash collision risk.

**Performance:** Lock is held for the full transaction duration. UNIQUE constraint conflict
is detected at INSERT time and the writer returns immediately without executing the handler.

**Consistency:** Both provide the same guarantee — exactly one winner. UNIQUE constraint
relies on PostgreSQL MVCC + unique index; no TOCTOU window because the INSERT itself is
the atomic check-and-reserve.

**Scalability:** Advisory locks are instance-scoped (single primary). UNIQUE constraints
work across all writers. Not relevant today (single primary), but the right foundation.

---

## Goals

- Pre-handler duplicate detection for command-mediated writes.
- Remove advisory lock from the command path without changing event-level semantics.
- Keep the command idempotency key on the command itself — controller stays agnostic.
- Keep the existing `IdempotencyKey` in `CommandDecision` as defense-in-depth.
- TTL-based cleanup so `command_idempotency` does not grow unboundedly.

## Non-Goals

- Remove the advisory lock from `append_events_if` (stays for event-level idempotency).
- Replace `IdempotencyKey` in `CommandDecision` (kept as complementary mechanism).
- Command-level idempotency for direct `EventStore` calls (no command context available).
- Distributed idempotency across multiple PostgreSQL primaries.

---

## Delivery Scope

Three steps, each independently deployable:

1. **`command_idempotency` schema** — table, index, migration. No behavior change.
2. **`IdempotentCommand` interface** — opt-in public API on `crablet-commands`. No executor
   change yet; commands can implement the interface before the executor uses it.
3. **`CommandExecutorImpl` integration** — check and reserve before handler; release on
   rollback; cleanup scheduler.

---

## Step 1 — Schema

```sql
CREATE TABLE command_idempotency (
    idempotency_key TEXT                     NOT NULL PRIMARY KEY,
    command_type    VARCHAR(64)              NOT NULL,
    transaction_id  xid8,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_command_idempotency_expires_at
    ON command_idempotency (expires_at);
```

`idempotency_key` is the PRIMARY KEY — the uniqueness constraint that replaces the advisory
lock. `transaction_id` is set after successful execution (NULL until then). `expires_at`
drives TTL-based cleanup.

The `idx_command_idempotency_expires_at` index supports efficient cleanup queries that
delete expired rows.

---

## Step 2 — `IdempotentCommand` Interface

Add to `crablet-commands` public API:

```java
@Stable
public interface IdempotentCommand {

    /**
     * A stable, unique key for this command execution derived from the command's own fields.
     *
     * <p>The key must be:
     * <ul>
     *   <li>Deterministic — same command fields produce the same key.</li>
     *   <li>Scoped — different operations on different entities must produce different keys.</li>
     *   <li>Stable — the key must not change between retries of the same operation.</li>
     * </ul>
     *
     * <p>Use business keys, not random IDs. Examples:
     * {@code "subscribe-student:" + studentId + ":" + courseId} or
     * {@code "open-wallet:" + walletId}.
     */
    String idempotencyKey();
}
```

The controller that creates the command is unaffected — it constructs the record from its
own fields. The command derives the key from those same fields:

```java
public record SubscribeStudentToCourseCommand(String studentId, String courseId)
        implements CourseCommand, IdempotentCommand {

    @Override
    public String idempotencyKey() {
        return "subscribe-student:" + studentId + ":" + courseId;
    }
}
```

### Key design rules

- **Do not use UUIDs or random values.** A random key defeats idempotency — retries produce
  a new key and are treated as new operations.
- **Scope to the operation, not the entity.** `open-wallet:w1` and `close-wallet:w1` must
  be different keys even though they share `walletId`.
- **Include all discriminating fields.** If `courseId` and `studentId` together uniquely
  identify the subscription attempt, both must appear in the key.

### Retention

`CommandExecutorImpl` will pass a configurable TTL when inserting idempotency records.
Default: 30 days. Configure via:

```properties
crablet.commands.idempotency.retention-days=30
```

After the TTL, a duplicate submission of the same key is treated as a new execution.
Set the retention to match the maximum expected retry window for your callers.

---

## Step 3 — `CommandExecutorImpl` Integration

### Transaction flow

```
BEGIN

  INSERT INTO command_idempotency (idempotency_key, command_type, occurred_at, expires_at)
  VALUES (?, ?, NOW(), NOW() + INTERVAL '? days')
  ON CONFLICT (idempotency_key) DO NOTHING

  IF rows_affected == 0:
    → return ExecutionResult.idempotent("COMMAND_DUPLICATE")   ← pre-handler short-circuit
    → COMMIT (or ROLLBACK — no events written, either is fine)

  execute handler (project decision model, validate, decide)

  append events (appendCommutative / appendNonCommutative / appendIdempotent)

  UPDATE command_idempotency
  SET transaction_id = pg_current_xact_id()
  WHERE idempotency_key = ?

  store command audit (if enabled)

COMMIT

ON ROLLBACK:
  command_idempotency INSERT is also rolled back — key is released.
  Next retry will see no record and proceed.
```

### Concurrency behavior under this design

Two concurrent writers with the same key:
- Writer A: INSERT succeeds (1 row), holds the row lock until commit/rollback.
- Writer B: INSERT blocks on the unique index row lock (not an advisory lock).
- A commits → B's INSERT gets conflict (0 rows) → B returns idempotent.
- A rolls back → B's INSERT succeeds (1 row) → B proceeds as new.

This is correct idempotency semantics with no advisory lock, no hash collision risk, and
per-key isolation — writers with different keys never interact.

### Integration point in `CommandExecutorImpl`

The INSERT must happen inside `eventStore.executeInTransaction`. The executor accesses the
connection via `txStore instanceof CommandAuditStore` today for audit writes. Extend the
same pattern with a `CommandIdempotencyStore` interface:

```java
public interface CommandIdempotencyStore {
    boolean reserveIfAbsent(String idempotencyKey, String commandType, int retentionDays);
    void recordTransactionId(String idempotencyKey, String transactionId);
}
```

`reserveIfAbsent` returns `true` if the key was newly reserved (proceed), `false` if it
already existed (duplicate). `ConnectionScopedEventStore` implements this interface using
the scoped connection.

The executor check:

```java
if (command instanceof IdempotentCommand ic && txStore instanceof CommandIdempotencyStore cs) {
    boolean reserved = cs.reserveIfAbsent(
        ic.idempotencyKey(), commandType, config.getIdempotencyRetentionDays());
    if (!reserved) {
        return ExecutionResult.idempotent("COMMAND_DUPLICATE");
    }
}
```

After successful append:

```java
if (command instanceof IdempotentCommand ic && txStore instanceof CommandIdempotencyStore cs
        && transactionId != null) {
    cs.recordTransactionId(ic.idempotencyKey(), transactionId);
}
```

### Cleanup

Add a scheduled task that deletes expired records:

```sql
DELETE FROM command_idempotency
WHERE expires_at < NOW()
LIMIT 1000;
```

Run on a configurable interval (default: every hour). Batch-delete with `LIMIT` to avoid
long-running transactions. Log deleted count for observability.

Disable cleanup if `crablet.commands.idempotency.retention-days=0` (retain forever).

---

## Relationship to Existing `IdempotencyKey`

| Mechanism | When checked | What it checks | Lock |
|-----------|-------------|----------------|------|
| `IdempotentCommand.idempotencyKey()` | Pre-handler | Has this command been processed? | UNIQUE constraint |
| `CommandDecision.IdempotencyKey` | Post-handler (on append) | Do these output events already exist? | Advisory lock |

A command can use both: command-level for the fast pre-check, event-level as defense against
concurrent execution that slips through. The advisory lock is only reached if the command
does not implement `IdempotentCommand`, or if the command-level check passes but the
event-level check finds a conflict (edge case: TTL expired, or direct `appendIdempotent`
call).

---

## Rollout Sequence

1. Add `command_idempotency` schema (Step 1). No behavior change.
2. Add `IdempotentCommand` interface (Step 2). No executor change.
3. Add `CommandIdempotencyStore` interface and `ConnectionScopedEventStore` implementation.
4. Wire `CommandExecutorImpl` to check and reserve (Step 3).
5. Add cleanup scheduler.
6. Update `IntegrationTestDbCleanup` to truncate `command_idempotency`.
7. Add integration tests: duplicate detection pre-handler, rollback releases key, TTL expiry.

---

## Open Questions

- Should `command_idempotency` rows be exposed via an admin API or metrics endpoint?
  Useful for diagnosing retry storms. Not required for initial implementation.
- Should `reserveIfAbsent` also return the existing `transaction_id` on conflict, so callers
  can look up the original execution's result? Depends on whether callers need the original
  response or just "it was already done."
- Should cleanup be a framework-managed `@Scheduled` bean, or delegated to the application
  via a documented SQL procedure? Framework-managed is lower friction; application-delegated
  gives more control over timing and load.
