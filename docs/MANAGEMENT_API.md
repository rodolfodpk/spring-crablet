# Management API

Spring Crablet exposes a REST management API for monitoring and controlling views, outbox publishers, and automations at runtime. All endpoints are available in `wallet-example-app` at `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Views — `/api/views`

### Get all statuses
```bash
curl http://localhost:8080/api/views/status
```
```json
{
  "wallet-balance-view": "ACTIVE",
  "wallet-history-view": "ACTIVE"
}
```

### Get status + lag for one view
```bash
curl http://localhost:8080/api/views/wallet-balance-view/status
```
```json
{ "viewName": "wallet-balance-view", "status": "ACTIVE", "lag": 0 }
```

### Get detailed progress (position, errors, timestamps)
```bash
curl http://localhost:8080/api/views/wallet-balance-view/details
curl http://localhost:8080/api/views/details          # all views
```

### Get lag (events behind)
```bash
curl http://localhost:8080/api/views/wallet-balance-view/lag
```
```json
{ "viewName": "wallet-balance-view", "lag": 42 }
```

### Pause / Resume / Reset
```bash
curl -X POST http://localhost:8080/api/views/wallet-balance-view/pause
curl -X POST http://localhost:8080/api/views/wallet-balance-view/resume
curl -X POST http://localhost:8080/api/views/wallet-balance-view/reset   # clears FAILED state
```

---

## Outbox — `/api/outbox`

Publishers are identified by a `(topic, publisher)` pair.

### Get all statuses
```bash
curl http://localhost:8080/api/outbox/status
```
```json
{
  "wallet-events:LogPublisher": "ACTIVE"
}
```

### Get status + lag for one publisher
```bash
curl http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/status
```
```json
{ "topic": "wallet-events", "publisher": "LogPublisher", "status": "ACTIVE", "lag": 0 }
```

### Get detailed progress
```bash
curl http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/details
curl http://localhost:8080/api/outbox/details    # all publishers
```

### Get lag
```bash
curl http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/lag
```

### Pause / Resume / Reset
```bash
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/pause
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/resume
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/reset
```

---

## Automations — `/api/automations`

### Get all statuses
```bash
curl http://localhost:8080/api/automations/status
```
```json
{
  "wallet-notification": "ACTIVE"
}
```

### Get status + lag for one automation
```bash
curl http://localhost:8080/api/automations/wallet-notification/status
```
```json
{ "automationName": "wallet-notification", "status": "ACTIVE", "lag": 0 }
```

### Get detailed progress
```bash
curl http://localhost:8080/api/automations/wallet-notification/details
curl http://localhost:8080/api/automations/details    # all automations
```

### Get lag
```bash
curl http://localhost:8080/api/automations/wallet-notification/lag
```

### Pause / Resume / Reset
```bash
curl -X POST http://localhost:8080/api/automations/wallet-notification/pause
curl -X POST http://localhost:8080/api/automations/wallet-notification/resume
curl -X POST http://localhost:8080/api/automations/wallet-notification/reset
```

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
curl -s http://localhost:8080/api/views/status && \
curl -s http://localhost:8080/api/outbox/status && \
curl -s http://localhost:8080/api/automations/status
```

---

## Webhook publisher convention — tags as HTTP headers

When implementing an `OutboxPublisher` that posts events to an HTTP webhook,
the recommended convention is to forward event tags and metadata as HTTP headers
so consumers can route or filter without parsing the body:

```
POST /your-webhook
X-Crablet-Event-Type:   DepositMade
X-Crablet-Position:     4521
X-Crablet-Occurred-At:  2026-04-06T15:00:00Z
X-Crablet-Tag-Wallet-Id: alice
X-Crablet-Tag-Deposit-Id: dep-123

{ "depositId": "dep-123", "walletId": "alice", "amount": 100 }
```

Header naming: `X-Crablet-Tag-{Tag-Key-In-Train-Case}`.
`StoredEvent.tags()` is available in `publishBatch()` to build these headers.
