# Plan: LISTEN/NOTIFY Improvements for Monolith and Distributed Deployments

## Context

`pg_notify` fires after every successful event append. Every listening Postgres
connection receives the notification and calls `requestImmediatePoll()` on all
enabled processors in its module — regardless of whether the appended events are
relevant to any of them. Two structural inefficiencies:

1. **Wasted wakeup calls within a batch**: `getNotifications(1000)` can return N
   notifications; the current loop calls `onWakeup.run()` N times, causing N
   redundant cancel+reschedule cycles per module.

2. **Wasted wakeups across unrelated pollers**: A `WalletDeposited` event wakes
   views that only care about `CourseEnrolled`, automations scoped to inventory
   events, and every outbox topic — all of which hit the DB and find nothing.

3. **Redundant LISTEN connections in monolith**: views, automations, and outbox
   each call `factory.create()`, opening 3 dedicated Postgres connections on
   the same channel.

**Deployment constraints:**
- Monolith: all three apply.
- Distributed (one service per module): #1 and #2 apply per instance; #3 is moot.
- Scale-to-zero (KEDA): LISTEN cannot wake a zero-replica pod — KEDA's PostgreSQL
  lag scaler handles scale-from-zero. Out of scope; already documented.

---

## Phase 1 — Batch coalesce

**Scope**: `PostgresNotifyWakeupSource.listenLoop()` only.

Today the loop calls `onWakeup.run()` once per `PGNotification` in the array.
With the current fixed payload `"events-appended"` all notifications in a batch
are equivalent, so collapsing to one call per batch is safe and correct.

**Important caveat**: Once Phase 3 adds type information to payloads, individual
notifications in a batch can differ. Phase 1 and Phase 3 must therefore define a
joint batch rule (see Phase 3 below). Phase 1 alone is low-risk only because
payloads are currently identical; document this dependency explicitly.

**Change** in `PostgresNotifyWakeupSource.listenLoop()`:
```java
// Before
for (PGNotification ignored : notifications) {
    if (!running.get()) break;
    onWakeup.run();
}

// After (Phase 1 only — single subscriber; Phase 2/3 change this further)
if (running.get()) {
    onWakeup.run();
}
```

**Helps**: both deployment models.

---

## Phase 2 — Shared LISTEN connection with subscriber list

**Goal**: one connection per JVM regardless of how many modules are active.

### Lifecycle design

Today every `EventProcessorImpl` and `SharedFetchModuleProcessor` calls
`wakeupSource.close()` on `@PreDestroy`. With a singleton source, the first
processor to shut down would close the connection for all remaining processors.

**Resolution: subscriber registration + unregistration model.**

The shared source owns the connection lifecycle. Each module registers and
later unregisters. The LISTEN thread stops when the last subscriber unregisters.

```java
// ProcessorWakeupSource — extend interface
void start(Runnable onWakeup);        // register subscriber + start thread if needed
void close(Runnable onWakeup);        // unregister subscriber; stop thread if last
```

`EventProcessorImpl.shutdownSchedulers()` calls `wakeupSource.close(this::requestImmediatePoll)`.
`SharedFetchModuleProcessor` does the same. `NoopProcessorWakeupSource.close(Runnable)` is a no-op.

### Singleton factory

`PostgresNotifyWakeupSourceFactory` holds one `PostgresNotifyWakeupSource`
instance. `create()` always returns it. The factory registers `@PreDestroy` to
call the underlying source's no-arg `close()` (force-tear-down on context
shutdown regardless of subscriber count).

### Subscriber list in `PostgresNotifyWakeupSource`

```java
private final CopyOnWriteArrayList<Runnable> subscribers = new CopyOnWriteArrayList<>();

public synchronized void start(Runnable onWakeup) {
    subscribers.add(onWakeup);
    if (running.compareAndSet(false, true)) {
        listenerThread = new Thread(this::listenLoop, "crablet-pg-listen-" + channel);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
}

public synchronized void close(Runnable onWakeup) {
    subscribers.remove(onWakeup);
    if (subscribers.isEmpty()) {
        running.set(false);
        closeConnectionQuietly();
        if (listenerThread != null) { listenerThread.interrupt(); listenerThread = null; }
    }
}
```

`listenLoop()` broadcasts to all subscribers (Phase 1 coalesce applies):
```java
if (running.get()) {
    subscribers.forEach(Runnable::run);
}
```

### Startup ordering note

The LISTEN thread starts on the first `start()` call. Modules register during
`ContextRefreshedEvent` / `ApplicationReadyEvent` in undefined order. A
notification arriving before all three modules have registered is received by
only the registered subscribers — acceptable because the scheduled polling
fallback covers the startup window (`startup-delay-ms`).

### Distributed deployment

Transparent: each service has one module, so `create()` is still called once
per JVM. Singleton behaviour changes nothing in that case.

### Files changed in Phase 2

| File | Change |
|---|---|
| `crablet-event-poller/…/wakeup/ProcessorWakeupSource.java` | Add `close(Runnable)` overload |
| `crablet-event-poller/…/wakeup/PostgresNotifyWakeupSource.java` | Subscriber list; typed `close()` |
| `crablet-event-poller/…/wakeup/NoopProcessorWakeupSource.java` | Add no-op `close(Runnable)` |
| `crablet-event-poller/…/wakeup/PostgresNotifyWakeupSourceFactory.java` | Singleton `create()`; `@PreDestroy` |
| `crablet-event-poller/…/internal/EventProcessorImpl.java` | Call `close(this::requestImmediatePoll)` in shutdown |
| `crablet-event-poller/…/sharedfetch/SharedFetchModuleProcessor.java` | Same |
| `crablet-event-poller/…/config/EventPollerAutoConfiguration.java` | Ensure factory teardown ordering |

---

## Phase 3 — Event-type payload filtering

**Goal**: skip `requestImmediatePoll()` when the appended event types have zero
overlap with a module's declared subscriptions.

### Batch aggregation rule (joint spec with Phase 1)

`getNotifications(1000)` returns an array. Individual notifications in a batch
can carry different payloads once Phase 3 is active. The rule:

1. Collect all payloads in the batch.
2. If **any** payload is a wildcard (`"*"`, blank, or null) → treat whole batch
   as wildcard (wake all subscribers).
3. Otherwise → union all encoded type sets across the batch.
4. Run subscriber matching **once** against the unioned set.
5. Call `onWakeup.run()` for each subscriber whose declared types intersect the
   union (or whose declared types are empty = wildcard subscriber).

This means one `requestImmediatePoll()` call per subscriber per batch, matching
Phase 1's intent while handling heterogeneous payloads correctly.

### 3a. Notifier side

**`EventAppendNotifier`** — add typed overload with wildcard default:
```java
default void notifyEventsAppended(Set<String> eventTypes) {
    notifyEventsAppended(); // wildcard fallback for implementations not yet updated
}
```

**`PostgresNotifyEventAppendNotifier`** — implement typed overload:
```java
public void notifyEventsAppended(Set<String> eventTypes) {
    String payload;
    if (eventTypes.isEmpty()) {
        payload = "*";
    } else {
        String encoded = String.join(",", eventTypes);
        payload = encoded.length() <= 7900 ? encoded : "*"; // Postgres limit is 8000 bytes
    }
    // execute pg_notify(channel, payload)
}
```

**`EventStoreImpl.appendIf()`** — extract event type names (already in the
`AppendEvent` list before the SQL call) and call the typed overload:
```java
Set<String> appendedTypes = events.stream()
    .map(e -> EventType.type(e.getClass()))
    .collect(Collectors.toSet());
publishAppendNotification(appendedTypes);
```

**`EventStoreImpl.executeInTransaction()`** — keep calling no-arg overload
(sends `"*"`). The transaction-scoped store does not expose appended event types
at the outer level without additional plumbing. Acceptable trade-off; see ROI
note below.

**ROI note**: if a large share of real-world appends flow through the command
executor's `executeInTransaction` path, modules will frequently see `"*"` and
Phase 3 wins shrink. Validate with actual workload patterns. If needed,
`ConnectionScopedEventStore` can be extended to track appended type names and
expose them at commit time — leave as a follow-up.

### 3b. Listener side — subscriber registration with type set

**`ProcessorWakeupSource`** — add typed `start()`/`close()` overloads (default: wildcard):
```java
default void start(Set<String> subscribedEventTypes, Runnable onWakeup) {
    start(onWakeup);
}
default void close(Set<String> subscribedEventTypes, Runnable onWakeup) {
    close(onWakeup);
}
```

**`PostgresNotifyWakeupSource`** — subscriber record with type set:
```java
private record Subscriber(Set<String> eventTypes, Runnable onWakeup) {}
private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
```

Payload parsing (safe — no `Set.of` on split, normalise blank tokens):
```java
private static Set<String> parsePayload(String payload) {
    if (payload == null || payload.isBlank() || "*".equals(payload.trim())) {
        return Set.of(); // empty = wildcard
    }
    return Arrays.stream(payload.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
}
```

Batch dispatch applying the joint Phase 1+3 rule:
```java
boolean hasWildcard = false;
Set<String> batchTypes = new HashSet<>();
for (PGNotification n : notifications) {
    Set<String> parsed = parsePayload(n.getParameter());
    if (parsed.isEmpty()) { hasWildcard = true; break; }
    batchTypes.addAll(parsed);
}

if (!running.get()) return;
for (Subscriber sub : subscribers) {
    boolean wake = sub.eventTypes().isEmpty()                       // subscriber is wildcard
        || hasWildcard                                              // sender is wildcard
        || !Collections.disjoint(sub.eventTypes(), batchTypes);   // type intersection
    if (wake) sub.onWakeup().run();
}
```

### 3c. Module auto-configurations — compute module event-type union

Each auto-configuration computes the module-level event-type union at startup
and passes it when registering the wakeup subscriber.

Pattern (same logic for automations and outbox, using their subscription maps):
```java
// In ViewsAutoConfiguration, after building viewSubscriptions map:
boolean anyWildcard = viewSubscriptions.values().stream()
    .anyMatch(s -> s.getEventTypes().isEmpty());
Set<String> moduleTypes = anyWildcard
    ? Set.of()  // empty = wildcard: this module wants all event types
    : viewSubscriptions.values().stream()
        .flatMap(s -> s.getEventTypes().stream())
        .collect(Collectors.toUnmodifiableSet());
```

`moduleTypes` is passed to `EventProcessorImpl` as a new constructor parameter
(`Set<String> subscribedEventTypes`, default `Set.of()` via existing overloads).
`initializeSchedulersIfNeeded` calls:
```java
wakeupSource.start(subscribedEventTypes, this::requestImmediatePoll);
```
And `shutdownSchedulers` calls:
```java
wakeupSource.close(subscribedEventTypes, this::requestImmediatePoll);
```

### Files changed in Phase 3

| File | Change |
|---|---|
| `crablet-eventstore/…/notify/EventAppendNotifier.java` | Add typed `notifyEventsAppended(Set<String>)` |
| `crablet-eventstore/…/notify/PostgresNotifyEventAppendNotifier.java` | Encode types; truncation + wildcard fallback |
| `crablet-eventstore/…/internal/EventStoreImpl.java` | Call typed notifier from `appendIf()` |
| `crablet-event-poller/…/wakeup/ProcessorWakeupSource.java` | Add typed `start`/`close` overloads |
| `crablet-event-poller/…/wakeup/PostgresNotifyWakeupSource.java` | Subscriber record; payload parsing; batch dispatch |
| `crablet-event-poller/…/wakeup/NoopProcessorWakeupSource.java` | Add typed `start`/`close` no-ops |
| `crablet-event-poller/…/internal/EventProcessorImpl.java` | Accept `subscribedEventTypes`; call typed start/close |
| `crablet-event-poller/…/EventProcessorFactory.java` | Thread `subscribedEventTypes` through factory overloads |
| `crablet-views/…/config/ViewsAutoConfiguration.java` | Compute `moduleTypes`; pass to factory |
| `crablet-automations/…/config/AutomationsAutoConfiguration.java` | Same |
| `crablet-outbox/…/config/OutboxAutoConfiguration.java` | Same |

---

## Out of Scope

- **Scale-to-zero**: LISTEN cannot wake a zero-replica pod. KEDA handles
  scale-from-zero. Already documented in k8s skill and `DEPLOYMENT_TOPOLOGY.md`.
- **Tag-based payload filtering**: cannot be compactly encoded in 8000 bytes for
  the general case. Event-type filtering catches the dominant case.
- **Cross-instance deduplication**: standby replicas losing the leader election
  check is inherent overhead; leader election handles correctness.
- **`executeInTransaction` type tracking**: defer unless workload analysis shows
  the wildcard path dominates.

---

## Verification

- **Phase 1**: `./mvnw test -pl crablet-event-poller -Dtest=PostgresNotifyWakeupSourceTest` — verify single `onWakeup` call per batch; assert throughput unchanged (same events processed, fewer wakeup executions).
- **Phase 2**: unit test `factory.create()` returns same instance; unit test two `start()` calls accumulate two subscribers; unit test `close(subscriber)` removes only that subscriber and keeps thread running; unit test closing last subscriber stops thread.
- **Phase 3**: unit test `parsePayload()` for wildcard/blank/null/valid/duplicates/trailing-comma; unit test batch dispatch for wildcard payload, type match, type miss, mixed batch; integration test that a module with non-overlapping event types does NOT call `requestImmediatePoll()` when an irrelevant event is appended via `appendIf()`.
- **Full build**: `make install-all-tests` after all phases.
