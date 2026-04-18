# Configuration Reference

All `crablet.*` properties across every module, with defaults and descriptions.

## Quick Start

Minimal `application.properties` for a typical setup with views, automations, and outbox:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=postgres

# Views
crablet.views.enabled=true

# Automations
crablet.automations.enabled=true

# Outbox
crablet.outbox.enabled=true
crablet.outbox.topics.topics.my-topic.publishers=MyPublisher
```

Everything else has sensible defaults. Enable modules explicitly — they are all off by default.

---

## crablet-eventstore

### `crablet.eventstore`

| Property | Type | Default | Description |
|---|---|---|---|
| `persist-commands` | boolean | `true` | Persist commands to the `commands` audit table |
| `transaction-isolation` | String | `READ_COMMITTED` | JDBC transaction isolation level |
| `fetch-size` | int | `1000` | PostgreSQL fetch size hint for result-set streaming |

### `crablet.eventstore.notifications`

Controls the NOTIFY side — fires `pg_notify` on every successful append. Always active; no opt-in required. If nothing is LISTENing, PostgreSQL discards the notification silently.

| Property | Type | Default | Description |
|---|---|---|---|
| `channel` | String | `crablet_events` | PostgreSQL channel name. Must match `crablet.event-poller.notifications.channel` |
| `payload` | String | `events-appended` | Payload token sent with each notification |

### `crablet.eventstore.read-replicas`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Route read-only event fetches to a replica |
| `url` | String | — | JDBC URL for the read replica (or read-replica load balancer) |
| `hikari.username` | String | — | Username for replica connection pool |
| `hikari.password` | String | — | Password for replica connection pool |
| `hikari.maximum-pool-size` | int | `50` | Maximum HikariCP pool size |
| `hikari.minimum-idle` | int | `10` | Minimum idle connections |

---

## crablet-event-poller

### `crablet.event-poller`

| Property | Type | Default | Description |
|---|---|---|---|
| `scheduler.pool-size` | int | `5` | Scheduler thread pool size shared across all processors |
| `scheduler.await-termination-seconds` | int | `60` | Seconds to wait for thread pool shutdown |
| `leader-retry-cooldown-ms` | long | `5000` | Cooldown before retrying leader election after a failure |
| `startup-delay-ms` | long | `500` | Initial delay before pollers start after application ready |

### `crablet.event-poller.notifications`

Controls the LISTEN side — opt-in wakeup that cancels the current scheduled delay and triggers an immediate poll on each NOTIFY. See [LISTEN/NOTIFY Wakeup](#listennotify-wakeup) below for compatibility rules.

| Property | Type | Default | Description |
|---|---|---|---|
| `jdbc-url` | String | — | Direct JDBC URL to PostgreSQL for the persistent LISTEN connection. When absent, pure scheduled polling is used. **Must bypass any pooler.** |
| `channel` | String | `crablet_events` | Channel to LISTEN on. Must match `crablet.eventstore.notifications.channel` |
| `username` | String | — | Username for the LISTEN connection |
| `password` | String | — | Password for the LISTEN connection |

---

## crablet-views

### `crablet.views`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Enable view processing |
| `polling-interval-ms` | long | `1000` | How often to poll for new events (ms). Raise to 30 000+ when LISTEN wakeup is active |
| `batch-size` | int | `100` | Events processed per cycle per view |
| `fetch-batch-size` | int | `1000` | Events fetched per DB query (relevant in shared-fetch mode) |
| `leader-election-retry-interval-ms` | long | `30000` | How often followers re-attempt leader election to detect crashes |
| `max-errors` | int | `10` | Consecutive errors before a processor stops |
| `backoff-threshold` | int | `3` | Errors before exponential backoff activates |
| `backoff-multiplier` | int | `2` | Exponential backoff multiplier |
| `max-backoff-seconds` | int | `120` | Maximum backoff delay (seconds) |
| `shared-fetch.enabled` | boolean | `false` | One DB query per cycle serves all views. Requires schema migration V14. Reduces DB load when many views share the same event stream |

---

## crablet-outbox

### `crablet.outbox`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Enable outbox processing |
| `polling-interval-ms` | long | `1000` | Global polling interval (ms). Overridable per publisher |
| `batch-size` | int | `100` | Events published per cycle per publisher |
| `fetch-size` | int | `100` | PostgreSQL fetch size hint |
| `fetch-batch-size` | int | `1000` | Events fetched per DB query (shared-fetch mode) |
| `max-retries` | int | `3` | Retry attempts for failed publish operations |
| `retry-delay-ms` | long | `5000` | Delay between retries (ms) |
| `leader-election-retry-interval-ms` | long | `30000` | Follower re-election check interval |
| `backoff-enabled` | boolean | `true` | Enable exponential backoff on errors |
| `backoff-threshold` | int | `3` | Errors before backoff activates |
| `backoff-multiplier` | int | `2` | Exponential backoff multiplier |
| `backoff-max-seconds` | int | `120` | Maximum backoff delay (seconds) |
| `shared-fetch.enabled` | boolean | `false` | One DB query per cycle serves all outbox processors |

### `crablet.outbox.topics`

| Property | Type | Default | Description |
|---|---|---|---|
| `topics.<name>.required-tags` | String | — | Comma-separated tags all events must have |
| `topics.<name>.any-of-tags` | String | — | Comma-separated tags; events must have at least one |
| `topics.<name>.exact-tags.<key>` | String | — | Exact tag key-value pair filter |
| `topics.<name>.publishers` | String | — | Comma-separated publisher bean names assigned to this topic |
| `topics.<name>.publisher-configs[n].name` | String | — | Publisher name |
| `topics.<name>.publisher-configs[n].polling-interval-ms` | Long | — | Per-publisher polling interval override |

### `crablet.outbox.global-statistics`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Track and log global outbox statistics |
| `log-interval-seconds` | long | `30` | Seconds between statistics log lines |
| `log-level` | String | `INFO` | Log level for statistics output |

---

## crablet-automations

### `crablet.automations`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Enable automation processing |
| `polling-interval-ms` | long | `1000` | How often to poll for new events (ms) |
| `batch-size` | int | `100` | Events processed per cycle per automation |
| `fetch-batch-size` | int | `1000` | Events fetched per DB query (shared-fetch mode) |
| `leader-election-retry-interval-ms` | long | `30000` | Follower re-election check interval |
| `max-errors` | int | `10` | Consecutive errors before a processor stops |
| `backoff-threshold` | int | `3` | Errors before exponential backoff activates |
| `backoff-multiplier` | int | `2` | Exponential backoff multiplier |
| `max-backoff-seconds` | int | `120` | Maximum backoff delay (seconds) |
| `shared-fetch.enabled` | boolean | `false` | One DB query per cycle serves all automations |

---

## crablet-commands-web

### `crablet.commands.api`

| Property | Type | Default | Description |
|---|---|---|---|
| `base-path` | String | `/api/commands` | Base path for the generic REST command endpoint |

---

## LISTEN/NOTIFY Wakeup

Crablet uses two PostgreSQL mechanisms to keep pollers responsive:

- **NOTIFY** — fires automatically after every `appendCommutative` / `appendNonCommutative` / `appendIdempotent` call via `pg_notify`. Always on, no configuration needed. If nothing is LISTENing, PostgreSQL discards it at zero cost.
- **LISTEN** — opt-in. Set `crablet.event-poller.notifications.jdbc-url` to a direct PostgreSQL URL. The poller opens one persistent connection, issues `LISTEN <channel>`, and immediately triggers a poll cycle on each notification. Reduces end-to-end latency from the polling interval to milliseconds.

When LISTEN wakeup is active, raise `polling-interval-ms` to `30000` or more — scheduled polling becomes a safety net only.

### Compatibility

| Deployment | NOTIFY (write side) | LISTEN wakeup |
|---|---|---|
| Direct PostgreSQL connection | ✅ | ✅ |
| PgBouncer — session mode | ✅ | ✅ — point `jdbc-url` at PostgreSQL directly |
| PgBouncer — transaction mode | ✅ | ❌ — LISTEN requires session state |
| PgCat — session mode | ✅ | ✅ — point `jdbc-url` at PostgreSQL directly |
| PgCat — transaction mode | ✅ | ❌ |
| Aurora PostgreSQL (direct) | ✅ | ✅ |
| RDS Proxy | ✅ | ❌ — RDS Proxy uses transaction-mode pooling internally |

> **Rule:** `crablet.event-poller.notifications.jdbc-url` must be a direct connection to PostgreSQL — never a pooler URL.

### Channel alignment

`crablet.eventstore.notifications.channel` (NOTIFY) and `crablet.event-poller.notifications.channel` (LISTEN) must match. Both default to `crablet_events`. If you run multiple environments on the same PostgreSQL instance, use different channel names to avoid cross-environment wakeups.

### Example configuration

```properties
# LISTEN wakeup — direct connection bypassing PgBouncer
crablet.event-poller.notifications.jdbc-url=jdbc:postgresql://postgres-primary:5432/mydb
crablet.event-poller.notifications.username=crablet
crablet.event-poller.notifications.password=secret

# Raise interval — scheduled poll is now just a safety net
crablet.views.polling-interval-ms=30000
crablet.automations.polling-interval-ms=30000
crablet.outbox.polling-interval-ms=30000
```

---

## DataSource Routing

Crablet separates reads and writes explicitly:

| Operation | DataSource | Reason |
|---|---|---|
| Event appends (`EventStore`) | Write | All writes go to primary |
| Command audit | Write | Same transaction as event append |
| Progress tracking | Write | Must be durable and consistent |
| Leader election (advisory locks) | Write | Advisory locks are session-scoped |
| Event fetching (views, automations, outbox) | Read | Stateless; safe to serve from replica |
| View projection writes | Write | Upserts to materialized view tables |

When `crablet.eventstore.read-replicas.enabled=false` (default), both roles use `spring.datasource`. Enable read replicas to route fetches to a replica and reduce load on the primary.

---

## Tuning Tips

**shared-fetch mode** — enables one DB query per poll cycle that serves all views (or all automations) instead of one query per processor. Enable when you have many views or automations sharing the same event stream. Requires running schema migration V14.

**fetch-batch-size vs batch-size** — `fetch-batch-size` controls how many events are loaded from the DB per query; `batch-size` controls how many are handed to each processor per cycle. In shared-fetch mode, `fetch-batch-size` is the dominant knob.

**backoff** — when a processor encounters repeated errors it backs off exponentially: delay doubles every error (up to `max-backoff-seconds`) after `backoff-threshold` consecutive failures. This protects the database during downstream outages.

**leader-election-retry-interval-ms** — followers poll at this interval to detect that the leader has crashed. Lower values mean faster failover; the default of 30 s is appropriate for most deployments. Do not set below 5 s.

**Deployment instances** — prefer 1 application instance. Use 2 for active/failover. Extra replicas do not increase throughput for the same processor set; only the leader processes events.
