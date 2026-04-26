# Fault Recovery

Crablet poller-backed modules are designed to stop at the failed processor, keep the last committed
position intact, and resume from that position after the underlying problem is fixed. This guide
describes the production recovery playbooks for views, automations, and outbox publishers.

## Recovery Model

Commands append events transactionally. Views, automations, and outbox publishers process those
events asynchronously through the shared event-poller infrastructure.

For each processor Crablet tracks:

- `last_position`: highest event position successfully handled
- `status`: `ACTIVE`, `PAUSED`, or `FAILED`
- `error_count`, `last_error`, `last_error_at`: failure diagnostics

When a handler throws, Crablet records the error and leaves `last_position` unchanged. The next
successful run reprocesses the same event window, so handlers and publishers should be idempotent
or tolerate duplicate delivery.

## View Projector Crash Mid-Batch

Symptom:

- A view stops advancing.
- `/api/views/{viewName}/details` shows a stale `lastPosition`, non-zero `errorCount`, or
  `FAILED`.

Recovery:

1. Read `/api/views/{viewName}/details` and inspect `lastError`.
2. Fix the projector bug, schema mismatch, or bad data assumption.
3. Resume or reset the view:

```bash
curl -X POST http://localhost:8080/api/views/wallet-balance/resume
curl -X POST http://localhost:8080/api/views/wallet-balance/reset
```

Use `resume` when the view table contents are still valid and only processing paused or failed.
Use `reset` when the view rows may be corrupt or incomplete. A reset sets processor progress back
to zero so the view can be rebuilt from the event store. If your application owns derived view
tables, clear those rows in the same maintenance window before resetting progress.

Projector implementation rule:

- Write each event idempotently. Prefer `UPSERT`, deterministic primary keys, or duplicate-safe
  junction tables so replaying after a crash does not double count rows.

## Outbox Publisher Failure

Symptom:

- `/api/outbox/{topic}/publishers/{publisher}/details` shows increasing errors.
- Downstream delivery stops at one position.

Recovery:

1. Check publisher health and downstream availability.
2. Fix credentials, endpoint configuration, schema compatibility, or downstream outage.
3. Resume the publisher:

```bash
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/wallet-webhook/resume
```

If the publisher reached `FAILED`, reset only after confirming the downstream system can tolerate
redelivery from the stored `last_position`:

```bash
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/wallet-webhook/reset
```

Publisher implementation rule:

- Throw `PublishException` for retriable failures such as network errors and HTTP 5xx responses.
- Treat duplicate delivery as normal. Include event `position` or `transactionId` in outbound
  messages so downstream consumers can deduplicate.
- Do not advance progress by swallowing transport failures. Swallow only non-retriable problems
  that an operator has intentionally chosen to skip.

## Automation Handler Failure

Symptom:

- `/api/automations/{automationName}/details` shows failures.
- The command caused by an automation is missing.

Recovery:

1. Inspect `lastError` for the automation.
2. Fix the handler logic or command validation issue.
3. Resume or reset the automation processor:

```bash
curl -X POST http://localhost:8080/api/automations/wallet-opened-automation/resume
curl -X POST http://localhost:8080/api/automations/wallet-opened-automation/reset
```

Automation implementation rule:

- Return `AutomationDecision.ExecuteCommand` and let `CommandExecutor` enforce idempotency.
- For external side effects, use outbox instead of calling the external system directly from the
  automation handler.

## Database Loss During Polling

If PostgreSQL restarts or a connection is lost mid-cycle, Crablet treats the cycle as failed and
does not update processor progress. When the database is available again, the processor retries
from the last committed position.

Operational checks:

- Confirm Flyway migrations have run before starting poller-backed modules.
- Check leader election status if no processor is advancing.
- Use `pause` before planned maintenance when you want a quiet stop.
- Use `resume` after the database and downstream systems are healthy.

## When To Use Reset

Use reset for:

- Rebuilding a stale or corrupt view from the event store.
- Replaying an outbox publisher after intentionally restoring downstream deduplication state.
- Recovering after a handler bug wrote derived state incorrectly.

Avoid reset for:

- A transient database outage.
- A downstream HTTP/Kafka outage that can recover with retry.
- A bug you have not fixed yet.

Reset replays historical events. That is safe for deterministic projections and idempotent
publishers, but it can duplicate external side effects if downstream consumers do not deduplicate.

## Wallet Example

The wallet app exposes friendly management endpoints for all three poller-backed modules:

- `GET /api/views/status`
- `GET /api/automations/status`
- `GET /api/outbox/status`

The included `WalletWebhookPublisher` demonstrates the outbox recovery contract: 5xx and transport
failures throw `PublishException` so the event remains pending, while 4xx responses are logged as
non-retriable payload/configuration failures.
