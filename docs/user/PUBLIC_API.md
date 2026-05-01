# Public API Guidance

## Stability Policy

Crablet uses `@Stable` as a documented compatibility signal for application-facing APIs. It
does not currently mean compatibility is enforced by an annotation processor or binary
compatibility checker.

After `1.0`, breaking changes to stable APIs should be called out in
[Upgrade Guide](UPGRADE.md), and should use deprecation first when that is practical.

The stable core event-store API includes:

- `EventStore`
- `AppendEvent`
- `AppendCondition`
- `Query`, `QueryItem`, and `Tag`
- `ProjectionResult`
- supporting types commonly used with those APIs: `StreamPosition`, `StoredEvent`,
  `ConcurrencyException`, `DCBViolation`, and `EventStoreException`

The stable command API includes:

- `CommandHandler`
- `CommutativeCommandHandler`
- `NonCommutativeCommandHandler`
- `IdempotentCommandHandler`
- `CommandDecision`
- `CommandExecutor`
- `ExecutionResult`

## Generic Command HTTP API

Crablet keeps the generic command HTTP API intentionally small. It is useful for simple command
submission, tests, internal tools, and early adoption, but application-specific APIs should use
normal Spring controllers when HTTP semantics matter.

Use the generic command API when:

- clients can submit a JSON command with a `commandType`
- `201 Created`, `200 OK` for idempotent/no-op results, `400 Bad Request`, `404 Not Found`, and
  `409 Conflict` are enough
- the generic response body is enough for clients

Use custom `@RestController` endpoints when:

- the endpoint needs resource-specific paths, status codes, headers, validation, or OpenAPI detail
- the response needs domain data beyond command execution status
- the API is public-facing and should not expose generic command names as its contract

Reads should normally come from views, and integration events should normally leave the service via
outbox publishers. For the full generic command HTTP contract, see
[`crablet-commands-web/README.md`](../../crablet-commands-web/README.md).

For `crablet-commands-web`, the stable contract is the wire behavior: paths, status codes,
request JSON, response JSON, problem-detail types, and correlation header behavior. Controller
class names, Spring configuration classes, package names, and internal wiring are implementation
details. OpenAPI/springdoc output is useful documentation, but the runtime wire behavior is the
contract if generated artifacts lag.

The generic command API also supports opt-in UUID correlation headers. Enable
`crablet.commands.api.correlation-header-enabled=true` to accept and echo `X-Correlation-Id`
and store that UUID on appended events.

## Direct `EventStore` Transactions

Most application writes should go through `CommandExecutor`, which handles command validation,
decision dispatch, append semantics, command audit, metrics, and transaction boundaries.

For advanced direct `EventStore` usage, `EventStore.executeInTransaction(...)` is public. It gives
the callback a transaction-scoped `EventStore` so projections, existence checks, appends, and
optional command audit via `CommandAuditStore` use the same JDBC connection and configured
transaction isolation. The transaction commits atomically on success and rolls back on error.

Append wakeups use PostgreSQL `NOTIFY`. When `pg_notify` runs inside an open transaction,
PostgreSQL delivers the notification only after the transaction commits, so listeners observe
append wakeups after successful commit.

## Idempotent Append Overloads

`EventStore.appendIdempotent(events, eventType, tagKey, tagValue)` is the simple common-case
overload for entity creation or duplicate-operation checks keyed by one event type and one tag.

`EventStore.appendIdempotent(events, idempotencyQuery)` is the advanced overload for checks that
need multiple event types, multiple tags, or a pre-built `Query`.
