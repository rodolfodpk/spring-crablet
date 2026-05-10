# Management API

Spring Crablet currently ships one built-in REST management API for poller-backed processors: `/api/processors`.

This API lives in the framework itself and manages the shared background workers behind views, outbox publishers, and automations.

The module-specific endpoints shown in `examples/wallet-example-app` such as `/api/views`, `/api/outbox`, and `/api/automations` are application-level APIs, not generic framework endpoints.

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Built-In Framework API — `/api/processors`

### Get all processor statuses
```bash
curl http://localhost:8080/api/processors
```
```json
{
  "wallet-balance-view": "ACTIVE",
  "wallet-transaction-view": "ACTIVE",
  "wallet-events:LogPublisher": "ACTIVE",
  "wallet-opened-welcome-notification": "ACTIVE"
}
```

### Get one processor status
```bash
curl http://localhost:8080/api/processors/wallet-balance-view
```
```json
{ "status": "ACTIVE" }
```

### Pause / Resume / Reset
```bash
curl -X POST http://localhost:8080/api/processors/wallet-balance-view/pause
curl -X POST http://localhost:8080/api/processors/wallet-balance-view/resume
curl -X POST http://localhost:8080/api/processors/wallet-balance-view/reset
```

### Get lag
```bash
curl http://localhost:8080/api/processors/wallet-balance-view/lag
```
```json
42
```

### Get backoff info
```bash
curl http://localhost:8080/api/processors/wallet-balance-view/backoff
curl http://localhost:8080/api/processors/backoff
```

---

## What A “Processor” Means

`/api/processors` manages the shared poller runtime abstraction. In practice, processor IDs usually correspond to:

- views
- outbox `(topic, publisher)` workers
- automations

Framework code does not currently ship generic module-specific aliases such as `/api/views`, `/api/outbox`, or `/api/automations`.

---

## Processor statuses

| Status | Meaning |
|--------|---------|
| `ACTIVE` | Processing normally |
| `PAUSED` | Manually paused — will not process until resumed |
| `FAILED` | Stopped after exceeding max error count — call `/reset` to recover |

---

## Quick health check (all processors)

```bash
curl -s http://localhost:8080/api/processors
```

---

## Example-App APIs

`examples/wallet-example-app` may expose friendlier application-level management endpoints such as:

- `/api/views/...`
- `/api/outbox/...`
- `/api/automations/...`

Those endpoints are part of the example application, not part of Crablet's built-in framework API.

---

## Custom publisher convention — preserve event metadata

When implementing an `OutboxPublisher`, preserve event tags and metadata in the
outbound payload or metadata so consumers can route or filter without parsing the body:

```
eventType: DepositMade
position: 4521
occurredAt: 2026-04-06T15:00:00Z
tags:
  wallet_id: alice
  deposit_id: dep-123
payload:
  depositId: dep-123
  walletId: alice
  amount: 100
```

`StoredEvent.tags()` is available in `publishBatch()` to build destination-specific metadata.
