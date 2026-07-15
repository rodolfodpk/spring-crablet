# Command-Level Idempotency Plan

> Status: revised plan. V10 is still on an unmerged feature branch, so replace
> V10's content instead of adding V11.

## Context

Command-level idempotency was first implemented in V10 with an
`idempotency_key TEXT` column and a partial unique index on `commands`.
The preferred design is simpler: the client controls command identity.

A caller-generated UUID becomes the `commands` table primary key:

- `command_id UUID PRIMARY KEY`
- UUID v7 is recommended for chronological ordering and B-tree locality.
- `transaction_id xid8` remains stored, but is demoted from primary key to a
  regular column for event-to-command linkage.
- `idempotency_key` is never added.

Duplicate command detection becomes a single insert:

```sql
INSERT INTO commands (command_id, transaction_id, type, data, metadata, occurred_at)
VALUES (COALESCE(?::uuid, gen_random_uuid()), pg_current_xact_id(), ?, ?::jsonb, ?::jsonb, ?)
ON CONFLICT (command_id) DO NOTHING
```

If a caller supplies `commandId`, `ON CONFLICT` detects committed duplicates.
If `commandId` is absent, `gen_random_uuid()` fills it and the path behaves as
normal non-idempotent command audit persistence.

## API Shape

Avoid overloading bare `UUID` parameters. `correlationId` and `commandId` are
both UUIDs but mean different things, so parameter order alone is unsafe.

Add a builder-backed options object:

```java
package com.crablet.command;

import com.crablet.eventstore.Stable;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@Stable
public record CommandExecutionOptions(
        @Nullable UUID correlationId,
        @Nullable UUID commandId
) {
    public static CommandExecutionOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable UUID correlationId;
        private @Nullable UUID commandId;

        public Builder correlationId(UUID correlationId) {
            this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
            return this;
        }

        public Builder commandId(UUID commandId) {
            this.commandId = Objects.requireNonNull(commandId, "commandId must not be null");
            return this;
        }

        public CommandExecutionOptions build() {
            return new CommandExecutionOptions(correlationId, commandId);
        }
    }
}
```

Update `CommandExecutor` to make the common path simple and the optional values
explicit:

```java
<T> ExecutionResult execute(T command);

<T> ExecutionResult execute(T command, CommandExecutionOptions options);

<T> ExecutionResult execute(T command, CommandHandler<T> handler);
```

Remove the string idempotency overloads:

```java
<T> ExecutionResult execute(T command, String idempotencyKey);
<T> ExecutionResult execute(T command, @Nullable UUID correlationId, String idempotencyKey);
```

The existing `execute(T, @Nullable UUID correlationId)` convenience overload may
be kept temporarily for compatibility, implemented as:

```java
if (correlationId == null) {
    return execute(command);
}
return execute(command, CommandExecutionOptions.builder()
        .correlationId(correlationId)
        .build());
```

If the branch can take the clean break, remove it too and require the options
object for all non-default execution.

Example call sites:

```java
executor.execute(command);

executor.execute(command, CommandExecutionOptions.builder()
        .correlationId(correlationId)
        .build());

executor.execute(command, CommandExecutionOptions.builder()
        .commandId(commandId)
        .build());

executor.execute(command, CommandExecutionOptions.builder()
        .correlationId(correlationId)
        .commandId(commandId)
        .build());
```

The builder setters reject `null`; absence is expressed by not calling a setter.
Internally, the record fields are nullable because both values are independently
optional.

## Migration

Rename both V10 copies:

```text
V10__commands_idempotency_key.sql -> V10__commands_command_id.sql
```

Files:

- `crablet-db-migrations/src/main/resources/db/migration/`
- `crablet-test-support/src/main/resources/db/migration/`

Both files must be identical.

New content:

```sql
-- Replace transaction_id as PK with client-controlled command_id UUID.
-- Clients should generate UUID v7 (time-ordered, B-tree friendly) using any library.
-- Server fallback uses gen_random_uuid() (v4), which is acceptable for the
-- non-idempotent path because it needs no deduplication.
-- When the project upgrades to PostgreSQL 18, this can become gen_uuid_v7()
-- with no other design change. pg_uuidv7 can bridge the gap on PG 17 if
-- server-side v7 is ever needed here, but it is not required for correctness.
-- transaction_id is retained as a regular column for event-to-command linkage.

ALTER TABLE commands ADD COLUMN command_id UUID;
UPDATE commands SET command_id = gen_random_uuid() WHERE command_id IS NULL;
ALTER TABLE commands ALTER COLUMN command_id SET NOT NULL;
ALTER TABLE commands DROP CONSTRAINT commands_pkey;
ALTER TABLE commands ADD PRIMARY KEY (command_id);
CREATE INDEX idx_commands_transaction_id ON commands (transaction_id);
```

Verify that `gen_random_uuid()` is available in the supported PostgreSQL
baseline. If not guaranteed by the baseline, add or verify
`CREATE EXTENSION IF NOT EXISTS pgcrypto;` in an earlier migration.

Events stay unchanged. They currently use `position BIGSERIAL` as primary key;
no event UUID exists. Server-side UUID v7 for events is a separate future
concern if an event UUID is ever added.

## CommandAuditStore

Unify command audit writes to one method:

```java
/**
 * Write a command record within the current transaction using a single SQL path.
 *
 * Executes:
 *   INSERT INTO commands (command_id, transaction_id, type, data, metadata, occurred_at)
 *   VALUES (COALESCE(?::uuid, gen_random_uuid()), pg_current_xact_id(), ...)
 *   ON CONFLICT (command_id) DO NOTHING
 *
 * If commandId is non-null, it is used as the primary key and this returns
 * false when a committed command already exists.
 *
 * If commandId is null, gen_random_uuid() fills it and conflicts are not
 * expected; this returns true.
 *
 * transaction_id is always pg_current_xact_id(), so this must be called inside
 * the active transaction when event-to-command linkage matters.
 */
boolean storeCommand(String commandJson, String commandType,
                     @Nullable UUID commandId, Instant occurredAt);
```

Remove `reserveCommand` entirely. Remove the default fallback. `storeCommand` is
the only command-write method.

Remove the old `storeCommand(commandJson, commandType, transactionId)` method.
The transaction ID parameter is no longer needed because SQL captures
`pg_current_xact_id()` on the active connection.

## EventStoreImpl

Replace `INSERT_COMMAND_SQL` and `RESERVE_COMMAND_SQL` with one constant:

```sql
INSERT INTO commands (command_id, transaction_id, type, data, metadata, occurred_at)
VALUES (COALESCE(?::uuid, gen_random_uuid()), pg_current_xact_id(), ?, ?::jsonb, ?::jsonb, ?::TIMESTAMP WITH TIME ZONE)
ON CONFLICT (command_id) DO NOTHING
```

Parameter order:

1. `commandId`
2. `type`
3. `data`
4. `metadata`
5. `occurredAt`

Use `setObject(1, commandId)`; when `commandId` is `null`, pass null.

Remove:

- `reserveCommandWithConnection`
- old `storeCommandWithConnection(Connection, commandJson, commandType, transactionId)`
- `ConnectionScopedEventStore.reserveCommand`

Add:

```java
private boolean storeCommandWithConnection(Connection connection,
                                           String commandJson,
                                           String commandType,
                                           @Nullable UUID commandId,
                                           Instant occurredAt)
```

It executes `STORE_COMMAND_SQL` and returns `stmt.executeUpdate() == 1`.

`ConnectionScopedEventStore.storeCommand` delegates to this private method.

The outer `EventStoreImpl.storeCommand` also uses the new signature. It opens a
connection and calls `storeCommandWithConnection`. Because this top-level path
uses a fresh transaction, tests that need event-to-command `transaction_id`
linkage must use `executeInTransaction(...)` and call `storeCommand` on the
transaction-scoped store.

## CommandExecutorImpl

Change execution internals to receive `CommandExecutionOptions`:

```java
private <T> ExecutionResult executeCore(
        T command,
        CommandHandler<T> handler,
        CommandExecutionOptions options)
```

Extract:

```java
@Nullable UUID commandId = options.commandId();
@Nullable UUID correlationId = options.correlationId();
```

If `correlationId` is present, bind it with `CorrelationContext` around the core
execution. Do not overload a raw UUID to mean either correlation or command ID.

Guard:

```java
if (commandId != null && !config.isPersistCommands()) {
    throw new InvalidCommandException(
            "Command-level idempotency requires persist-commands=true", command);
}
```

Pre-handler idempotency block:

```java
if (commandId != null && commandJson != null && txStore instanceof CommandAuditStore auditStore) {
    if (!auditStore.storeCommand(commandJson, commandType, commandId, startTime)) {
        operationType.set("command_idempotent");
        return ExecutionResult.idempotent("COMMAND_DUPLICATE");
    }
}
```

Post-append audit block for the non-idempotent path:

```java
if (commandId == null
        && config.isPersistCommands()
        && commandJson != null
        && txStore instanceof CommandAuditStore auditStore) {
    auditStore.storeCommand(commandJson, commandType, null, startTime);
}
```

No `commandReserved` guard is needed because the paths are mutually exclusive
through `commandId == null` vs `commandId != null`.

## Test Updates

`CommandExecutorImplCommandIdempotencyTest`

- Replace string keys with `UUID commandId = UUID.randomUUID()`.
- Replace `execute(command, key)` with:

```java
execute(command, CommandExecutionOptions.builder().commandId(commandId).build())
```

- Remove `nullKeyExecutesNormally`; builder setters reject null and absence is
  represented by omitting `commandId`.

`CommandExecutorImplPersistenceTest`

- Replace `execute(command, "some-idempotency-key")` with options carrying a
  random command ID.

`CommandAuditStoreTest`

- Update to the new `storeCommand("json", "type", UUID.randomUUID(), Instant.now())`
  contract, or remove the old default-method test because no default method
  remains.

`AutomationDefinitionConsistencyTest`

- Update anonymous `CommandExecutor` stubs to implement the options-based
  `execute` method instead of string idempotency overloads.

`EventStoreImplTest`

- Do not append with top-level `appendCommutative` and then call top-level
  `storeCommand(..., null, ...)` expecting the same `transaction_id`.
- Rewrite the command audit linkage test to use `executeInTransaction(...)`,
  append events through the transaction-scoped store, then call
  `((CommandAuditStore) txStore).storeCommand(..., null, occurredAt)` on the
  same scoped store.
- The verification query can continue using
  `WHERE transaction_id = ?::xid8` because the column still exists.

`EventStoreTest`

- Update transaction-scoped command audit calls to:

```java
auditStore.storeCommand(commandJson, commandType, null, occurredAt)
```

`InMemoryEventStore`

- Update both copies:
  - `crablet-test-support/src/main/java/com/crablet/test/InMemoryEventStore.java`
  - `crablet-commands/src/test/java/com/crablet/command/handlers/unit/InMemoryEventStore.java`
- New signature adds `@Nullable UUID commandId` and `Instant occurredAt`.
- Return `boolean`.
- Returning `true` is acceptable unless a test explicitly needs conflict
  detection.
- Remove `reserveCommand` overrides.

`CommandExecutorImplTest` and docs/web tests

- Search and update all `execute(command, correlationId)` call sites depending
  on whether the compatibility overload is kept.
- Search and update all `execute(command, idempotencyKey)` and
  `execute(command, correlationId, idempotencyKey)` call sites.

## Commands Web

Decide whether generic command HTTP execution should expose command-level
idempotency immediately.

Recommended behavior:

- Accept a command ID as a UUID header, for example `Idempotency-Key` or
  `X-Command-Id`.
- If using `Idempotency-Key`, require it to parse as UUID under the new design.
- Pass it through:

```java
commandExecutor.execute(command, CommandExecutionOptions.builder()
        .correlationId(correlationId)
        .commandId(commandId)
        .build());
```

If no command ID header is present, call with correlation only or default
options and the server-generated non-idempotent audit path is used.

## Documentation Updates

Update references in:

- `crablet-commands/README.md`
- `crablet-commands-web/README.md`
- `docs/user/UPGRADE.md`
- `docs/user/CORRELATION_CAUSATION.md` if the old correlation overload is removed
- any tutorial using `execute(command, idempotencyKey)`

Docs should say:

- command-level idempotency is based on caller-controlled `commandId UUID`;
- UUID v7 is recommended;
- duplicates return `ExecutionResult.idempotent("COMMAND_DUPLICATE")`;
- rollback releases the command ID because the insert rolls back atomically;
- `correlationId` and `commandId` are separate concepts.

## Verification

Run targeted tests first:

```sh
./mvnw test -pl crablet-eventstore -Dtest="CommandAuditStoreTest,EventStoreImplTest,EventStoreTest"
./mvnw test -pl crablet-commands
./mvnw test -pl crablet-automations -Dtest="AutomationDefinitionConsistencyTest"
```

Then run broader checks for modules affected by public API changes:

```sh
./mvnw test -pl crablet-commands-web
./mvnw test
```

## Open Decisions

- Whether to keep `execute(T, @Nullable UUID correlationId)` as a temporary
  compatibility overload or require `CommandExecutionOptions` for correlation.
- Whether the web module should use `Idempotency-Key` for UUID command IDs or a
  more explicit `X-Command-Id` header.
- Whether in-memory stores should implement real command ID conflict detection
  for tests or always return `true`.
