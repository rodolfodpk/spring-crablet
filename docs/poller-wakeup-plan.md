# Poller Wakeup Plan

## Goal

Reduce pointless idle polling without changing Crablet's correctness model.

Keep:
- PostgreSQL as source of truth
- progress tracking in PostgreSQL
- event fetches from `ReadDataSource`

Add:
- adaptive polling backoff
- optional PostgreSQL `LISTEN/NOTIFY` wakeups

Do not rely on notifications for correctness. They are only hints to poll immediately.

## Current Model

- processors poll on a fixed interval
- each processor queries the database even when idle
- low latency requires aggressive polling
- aggressive polling increases database load

## Target Model

1. Adaptive polling
- fixed polling interval becomes the minimum interval
- repeated empty polls increase delay
- successful fetch resets delay to minimum
- failures use a separate failure backoff
- jitter avoids synchronized polling bursts

2. Optional wakeups
- event append sends `NOTIFY`
- a dedicated listener connection does `LISTEN`
- wakeup marks processors as due immediately
- processors still fetch events from `ReadDataSource`

3. Replica-aware behavior
- `NOTIFY` may arrive before the read replica catches up
- immediate fetch may still return no rows
- on wakeup, do one short delayed retry before resuming normal backoff

## Important Compatibility Rule

Crablet does not currently use PostgreSQL `LISTEN/NOTIFY`.

If added:
- `NOTIFY` is fine on normal write connections
- `LISTEN` must use either:
  - a direct PostgreSQL connection, or
  - a PgBouncer/pgcat session-pooled connection

Do not use a transaction-pooled PgBouncer connection for `LISTEN`.

Normal event fetching can still go through `ReadDataSource`.

## Why Not Delegate Polling Backpressure to Resilience4j

Resilience4j is useful for protection, not scheduling.

Use Resilience4j for:
- retry
- timeout
- circuit breaker
- bulkhead / rate limit where needed

Do not use it for:
- deciding the next poll time
- replacing idle backoff logic

Split of responsibility:
- poller control loop decides when to try next
- Resilience4j guards what happens when a try occurs

## Proposed Types

### In `crablet-eventstore`

New package:
- `com.crablet.eventstore.notify`

New types:
- `EventAppendNotifier`
- `NoopEventAppendNotifier`
- `PostgresNotifyEventAppendNotifier`

Sketch:

```java
public interface EventAppendNotifier {
    void notifyEventsAppended();
}
```

`EventStoreImpl` should call the notifier after successful append commit.

Notification failures must be logged only, never fail event append.

### In `crablet-event-poller`

New package:
- `com.crablet.eventpoller.polling`

New types:
- `PollingBackoffPolicy`
- `ExponentialPollingBackoffPolicy`
- `ProcessorPollingState`

Sketch:

```java
public interface PollingBackoffPolicy {
    Duration nextDelayAfterEmpty(int consecutiveEmptyPolls);
    Duration nextDelayAfterFailure(int consecutiveFailures);
    Duration delayAfterSuccess();
}
```

```java
public final class ProcessorPollingState {
    int consecutiveEmptyPolls;
    int consecutiveFailures;
    Instant nextAllowedRunAt;
    boolean replicaCatchupRetryPending;
}
```

New wakeup package:
- `com.crablet.eventpoller.wakeup`

New types:
- `ProcessorWakeupSource`
- `NoopProcessorWakeupSource`
- `PostgresNotifyWakeupSource`

Sketch:

```java
public interface ProcessorWakeupSource extends AutoCloseable {
    void start(Runnable onWakeup);
}
```

## Polling Loop Behavior

For each processor:

1. If not due yet, skip
2. Fetch events
3. If fetch returns events:
- process
- update progress
- reset backoff to minimum
4. If fetch returns empty:
- if wakeup-triggered replica retry is pending, schedule one short retry
- otherwise increase empty-poll backoff
5. If fetch or handling fails:
- increase failure backoff

Wakeup behavior:
- mark processor or processor group as due immediately
- schedule immediate execution

## Configuration

### `crablet-event-poller`

Suggested properties:

```properties
crablet.event-poller.min-poll-interval-ms=1000
crablet.event-poller.max-poll-interval-ms=30000
crablet.event-poller.backoff-multiplier=2.0
crablet.event-poller.jitter-factor=0.2
crablet.event-poller.notifications.enabled=false
crablet.event-poller.notifications.channel=crablet_events
crablet.event-poller.notifications.jdbc-url=
crablet.event-poller.notifications.username=
crablet.event-poller.notifications.password=
crablet.event-poller.replica-catchup-delay-ms=200
```

### `crablet-eventstore`

Suggested properties:

```properties
crablet.eventstore.notifications.enabled=false
crablet.eventstore.notifications.channel=crablet_events
crablet.eventstore.notifications.jdbc-url=
crablet.eventstore.notifications.username=
crablet.eventstore.notifications.password=
```

The notification listener connection should be:
- direct PostgreSQL, or
- a session-pooled endpoint

## Rollout Order

Recommended implementation order:

1. Adaptive polling only
- add poller properties
- add polling state and backoff policy
- update `EventProcessorImpl`

2. Optional notifications
- add append notifier
- add wakeup source
- add `LISTEN/NOTIFY`

3. Replica catchup retry
- one quick retry after wakeup if replica is behind

4. Resilience4j integration
- wrap fetch with retry / timeout / circuit breaker
- keep scheduling logic in the poller

## Suggested Test Coverage

### `crablet-event-poller`

- resets to minimum delay after events are found
- backs off after repeated empty polls
- backs off after repeated failures
- jitter stays within configured range
- wakeup makes processor due immediately
- replica catchup retry runs once after wakeup

### `crablet-eventstore`

- append notifier is called after successful append
- notifier failure does not fail append
- notifications disabled uses noop notifier

## Final Recommendation

Best near-term path:

1. implement adaptive polling first
2. add optional PostgreSQL wakeups second
3. keep DB polling as the correctness path
4. use Resilience4j as a protective layer, not the poll scheduler

This gives lower idle DB load and lower latency without introducing a second source of truth.
