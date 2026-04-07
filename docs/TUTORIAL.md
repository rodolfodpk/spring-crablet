# Spring Crablet Tutorial: Conference Talk Submissions

This tutorial walks you through every major feature of Spring Crablet, from appending your first event to running concurrent DCB consistency checks. Each part introduces one concept, explains why it exists, and shows exactly how to use it.

**What you will build:** A conference talk submission system. Speakers submit talks, organizers accept or reject them, and a capacity constraint prevents more talks from being accepted than the conference can host.

**Prerequisites:** Java 25, Docker (for integration tests via Testcontainers), Maven.

**Reference implementation:** The `wallet-example-app` module contains a complete Spring Boot application demonstrating all these patterns against the wallet domain. When in doubt, look there.

**Modules used in this tutorial:**

| Feature | Module |
|---------|--------|
| Event store | `crablet-eventstore` |
| Commands | `crablet-commands` |
| Automations | `crablet-automations` |
| Views | `crablet-views` |
| Outbox | `crablet-outbox` |
| Testing | `crablet-test-support` |

---

## The Domain

```java
// Events
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TalkSubmitted.class, name = "TalkSubmitted"),
    @JsonSubTypes.Type(value = TalkAccepted.class,  name = "TalkAccepted"),
    @JsonSubTypes.Type(value = TalkRejected.class,  name = "TalkRejected")
})
public sealed interface TalkEvent permits TalkSubmitted, TalkAccepted, TalkRejected {}

public record TalkSubmitted(String talkId, String speakerId, String title) implements TalkEvent {}
public record TalkAccepted(String talkId, String speakerId)                implements TalkEvent {}
public record TalkRejected(String talkId, String speakerId, String reason) implements TalkEvent {}
```

```java
// Tag constants
public final class TalkTags {
    public static final String TALK_ID        = "talk_id";
    public static final String SPEAKER_ID     = "speaker_id";
    public static final String SUBMISSION_ID  = "submission_id";
    private TalkTags() {}
}
```

---

## Part 1: Your First Event

**Why this matters.** Event sourcing stores facts — things that happened — rather than the current state of objects. There is no `UPDATE` statement in this model: every change is a new record. The `EventStore` exposes three semantic write operations — `appendCommutative`, `appendNonCommutative`, and `appendIdempotent` — each expressing different concurrency semantics. Reading back requires projecting a sequence of those events into a state value.

Starting here, before commands or DCB, lets you see the raw mechanics without business rule complexity.

### Appending an event

```java
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.talks.TalkTags.TALK_ID;
import static com.crablet.examples.talks.TalkTags.SPEAKER_ID;

// Build the domain event
TalkSubmitted submitted = new TalkSubmitted("talk-1", "alice", "Event Sourcing in Practice");

// Wrap it in an AppendEvent — the write-side envelope
AppendEvent appendEvent = AppendEvent.builder(type(TalkSubmitted.class))
    .tag(TALK_ID, submitted.talkId())
    .tag(SPEAKER_ID, submitted.speakerId())
    .data(submitted)
    .build();

// appendCommutative: no conflict check needed.
// This is correct for a submission: two submissions of different talks
// do not interfere with each other.
eventStore.appendCommutative(List.of(appendEvent));
```

`EventType.type(TalkSubmitted.class)` returns the class simple name `"TalkSubmitted"`. This string must match the `name` in `@JsonSubTypes` on the sealed interface — using the helper everywhere eliminates typos.

Tags are key/value strings attached to events. They serve two purposes: filtering events when you project state (which events belong to *this* talk?), and defining DCB consistency boundaries (which events could conflict with this write?). Tags are stored as a PostgreSQL text array with a GIN index, making tag-based lookups fast even with millions of events.

### Reading events back

To read an event back you project it into a state type. The simplest projector just records whether the event exists:

```java
import com.crablet.eventstore.query.QueryBuilder;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.talks.TalkTags.TALK_ID;

var query = QueryBuilder.builder()
    .events(type(TalkSubmitted.class))
    .tag(TALK_ID, "talk-1")
    .build();

boolean exists = eventStore.exists(query);
```

`EventStore.exists(query)` is a convenience method that returns `true` if any event matching the query exists — no projector class needed.

`ProjectionResult` carries both the projected state and a stream position pointing to the last event that was read. You will use that stream position in Part 3. The convenience overload `project(query, projector)` implicitly starts from `StreamPosition.zero()` — the beginning of the event log.

> **Key insight.** Append methods never modify existing data — they only append. If you need to change the title of a talk, you append a `TalkTitleUpdated` event and project the latest title from the event sequence. The database table is an append-only log.

---

## Part 2: Rebuilding State from Events

**Why this matters.** Commands need to make decisions. To decide whether a talk can be accepted, a handler needs to know whether it has already been accepted or rejected. That decision state must be reconstructed from the event log each time — there is no mutable row to read.

This section shows how to build a full state projector and what the `ProjectionResult` carries.

### The state record

```java
public enum TalkStatus { PENDING, ACCEPTED, REJECTED }

public record TalkState(
    String talkId,
    String speakerId,
    TalkStatus status,
    boolean exists
) {
    public static TalkState empty() {
        return new TalkState(null, null, null, false);
    }

    public boolean isPending()  { return exists && status == TalkStatus.PENDING; }
    public boolean isAccepted() { return exists && status == TalkStatus.ACCEPTED; }
}
```

### The state projector

```java
import com.crablet.eventstore.query.StateProjector;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.StoredEvent;

import static com.crablet.eventstore.EventType.type;

public class TalkStateProjector implements StateProjector<TalkState> {

    // getId() not needed — defaults to class simple name

    @Override
    public List<String> getEventTypes() {
        return List.of(
            type(TalkSubmitted.class),
            type(TalkAccepted.class),
            type(TalkRejected.class)
        );
    }

    @Override
    public TalkState getInitialState() { return TalkState.empty(); }

    @Override
    public TalkState transition(TalkState state, StoredEvent event, EventDeserializer deserializer) {
        TalkEvent talkEvent = deserializer.deserialize(event, TalkEvent.class);
        return switch (talkEvent) {
            case TalkSubmitted s -> new TalkState(s.talkId(), s.speakerId(), TalkStatus.PENDING, true);
            case TalkAccepted a  -> new TalkState(state.talkId(), state.speakerId(), TalkStatus.ACCEPTED, true);
            case TalkRejected r  -> new TalkState(state.talkId(), state.speakerId(), TalkStatus.REJECTED, true);
        };
    }
}
```

Notice that `transition` receives a `StoredEvent` — the raw record from the database — and an `EventDeserializer`. The deserializer converts the stored JSON into a typed domain object. In unit tests, `InMemoryEventStore` provides a deserializer that skips JSON entirely and returns the original Java object directly.

### Projecting state for a specific talk

```java
var query = QueryBuilder.builder()
    .events(
        type(TalkSubmitted.class),
        type(TalkAccepted.class),
        type(TalkRejected.class)
    )
    .tag(TALK_ID, "talk-1")
    .build();

ProjectionResult<TalkState> result = eventStore.project(query, new TalkStateProjector());

TalkState state = result.state();
StreamPosition streamPosition = result.streamPosition(); // position of the last event read
```

The `streamPosition` field answers the question: "up to which event in the log did this projection read?" That answer is what makes concurrent writes safe — as you will see in the next part.

> **Key insight.** State is always derived, never stored independently. The event log is the source of truth. `result.streamPosition()` is the receipt for the version of reality you read — you will hand it back to the store when you write.

---

## Part 3: The DCB Moment

**Why this matters.** This is the core of the framework. Without it, concurrent commands can produce inconsistent state even when each individual command correctly validates its business rules. The DCB (Dynamic Consistency Boundary) pattern solves this without distributed locks.

### The problem: two organizers, one slot

Suppose the conference accepts a maximum of 2 talks. Two organizers open the system at the same moment. Each sees 1 accepted talk, concludes there is room for one more, and accepts a different pending talk. Without conflict detection, both writes succeed — and now 3 talks are accepted against a capacity of 2.

```java
// Thread A                                          // Thread B
ProjectionResult<ConferenceState> a                  ProjectionResult<ConferenceState> b
    = eventStore.project(                                = eventStore.project(
        conferenceQuery, conferenceProjector);               conferenceQuery, conferenceProjector);

// a.state().acceptedCount() == 1        // b.state().acceptedCount() == 1
// capacity check passes                 // capacity check passes

eventStore.appendCommutative(            eventStore.appendCommutative(
    List.of(acceptTalk3));  // BUG           List.of(acceptTalk4));  // BUG

// Result: 3 accepted talks. Capacity violated.
```

The bug is `appendCommutative`. It tells the store "order does not matter, do not check for conflicts". Both writes succeed independently because neither knows the other is happening.

### The fix: capture the stream position, check it on write

`ProjectionResult.appendNonCommutative` links the write to the version of reality that was read. The stream position is embedded in the result — it cannot be accidentally dropped:

```java
// Thread A — corrected
ProjectionResult<ConferenceState> result =
    eventStore.project(conferenceQuery, conferenceProjector);

if (result.state().acceptedCount() >= CAPACITY) {
    throw new ConferenceFullException();
}

// streamPosition is implicit — impossible to forget.
// Checks: has any event matching conferenceQuery been appended
// AFTER the captured stream position? If yes — throw ConcurrencyException.
result.appendNonCommutative(eventStore, List.of(acceptTalk3), conferenceQuery);
```

The check is atomic at the database level (implemented as a PostgreSQL stored function using advisory locks). Thread B's write will find that Thread A's `TalkAccepted` event now exists past Thread B's stream position, and will throw `ConcurrencyException`. The losing thread can retry from scratch: it will re-project, find 2 accepted talks, fail the capacity check, and correctly throw `ConferenceFullException`.

The `ConferenceStateProjector` counts `TalkAccepted` events across all talks — there is no per-talk stream boundary:

```java
public class ConferenceStateProjector implements StateProjector<ConferenceState> {

    // getId() not needed — defaults to class simple name

    @Override
    public List<String> getEventTypes() {
        return List.of(type(TalkAccepted.class));
    }

    @Override
    public ConferenceState getInitialState() { return new ConferenceState(0); }

    @Override
    public ConferenceState transition(ConferenceState state, StoredEvent event,
                                      EventDeserializer deserializer) {
        // Count every TalkAccepted regardless of which talk it belongs to
        return new ConferenceState(state.acceptedCount() + 1);
    }
}
```

The query that feeds this projector has no `talk_id` tag — it spans all talks in the conference:

```java
// Decision model: all TalkAccepted events (conference-wide capacity check)
var conferenceQuery = QueryBuilder.builder()
    .events(type(TalkAccepted.class))
    .tag("conference_id", conferenceId)  // scope to this conference
    .build();
```

This is the DCB insight: consistency boundaries are defined by the query, not by a fixed stream per entity. The conference capacity invariant requires reading across all talks, and the streamPosition-based check enforces it.

### Three patterns, one table

Not every command needs a streamPosition-based check. Choose the pattern that matches the semantics of the operation:

| Pattern | When to use | Method |
|---------|-------------|--------|
| **Commutative** | Order-independent operations — parallel writes cannot conflict (e.g., submitting a talk) | `appendCommutative(events)` |
| **Idempotent** | Entity creation — prevent duplicate creation (e.g., same submission submitted twice) | `appendIdempotent(events, eventType, tagKey, tagValue)` |
| **Non-commutative** | State-dependent operations — outcome depends on current state (e.g., accept/reject with capacity check) | `appendNonCommutative(events, decisionModel, streamPosition)` |

**Commutative** is appropriate when the operation is order-independent. Submitting two different talks simultaneously does not cause a conflict regardless of ordering.

**Idempotent** is for creation events. Rather than projecting state to check for existence (which requires a streamPosition-based check to be safe), you declare "fail if any event of this type with this tag already exists." The store checks this atomically. There is no state projection needed — the constraint is structural.

```java
// Prevent duplicate submission of the same submissionId
eventStore.appendIdempotent(List.of(appendEvent), type(TalkSubmitted.class), SUBMISSION_ID, command.submissionId());
```

**Non-commutative** is for anything where the decision depends on the current state and another concurrent writer could invalidate that decision. The accept-with-capacity-check is the canonical example.

> **Key insight.** DCB does not use distributed locks. The stream position is a "read version" — the position of the last event you read. If anything matching your decision model query has been written between your read and your write, the store rejects the write. You retry with a fresh projection. This is optimistic concurrency: assume no conflict, detect if one occurred.

---

## Part 4: Commands

**Why this matters.** Direct calls to `eventStore.appendCommutative` / `appendNonCommutative` / `appendIdempotent` work, but they leave you responsible for serializing the command for audit and managing the transaction. `CommandHandler` and `CommandExecutor` handle all of that, letting handlers focus on business logic.

### The CommandHandler interface

```java
// From com.crablet.command.CommandHandler
public interface CommandHandler<T> {
    CommandDecision handle(EventStore eventStore, T command);
}
```

Handlers never call append methods themselves. They project state, validate rules, build events, and return a `CommandDecision` — the framework picks the correct append method and wraps everything in a single database transaction. Three sub-interfaces make the DCB pattern explicit at the type level: `CommutativeCommandHandler`, `IdempotentCommandHandler`, and `NonCommutativeCommandHandler`.

### SubmitTalkCommandHandler — idempotency pattern

```java
import com.crablet.command.IdempotentCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.talks.TalkTags.SPEAKER_ID;
import static com.crablet.examples.talks.TalkTags.SUBMISSION_ID;
import static com.crablet.examples.talks.TalkTags.TALK_ID;

@Component
public class SubmitTalkCommandHandler implements IdempotentCommandHandler<SubmitTalkCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, SubmitTalkCommand command) {
        TalkSubmitted event = new TalkSubmitted(
            command.talkId(), command.speakerId(), command.title()
        );

        AppendEvent appendEvent = AppendEvent.builder(type(TalkSubmitted.class))
            .tag(TALK_ID, command.talkId())
            .tag(SPEAKER_ID, command.speakerId())
            .tag(SUBMISSION_ID, command.submissionId())
            .data(event)
            .build();

        // Idempotency check: fail if a TalkSubmitted with this submissionId already exists.
        // No state projection needed — the constraint is structural.
        return CommandDecision.Idempotent.of(appendEvent, type(TalkSubmitted.class), SUBMISSION_ID, command.submissionId());
    }
}
```

### AcceptTalkCommandHandler — streamPosition-based DCB pattern

This handler must enforce two invariants simultaneously: the talk must be in PENDING status, and the conference must not be at capacity. The capacity invariant is enforced with a streamPosition-based check.

```java
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.EventStore;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.talks.TalkTags.SPEAKER_ID;
import static com.crablet.examples.talks.TalkTags.TALK_ID;

@Component
public class AcceptTalkCommandHandler implements NonCommutativeCommandHandler<AcceptTalkCommand> {

    private static final int CONFERENCE_CAPACITY = 2;

    private final TalkStateProjector talkStateProjector = new TalkStateProjector();
    private final ConferenceStateProjector conferenceProjector = new ConferenceStateProjector();

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, AcceptTalkCommand command) {
        // 1. Project state for this specific talk
        Query talkQuery = QueryBuilder.builder()
            .events(
                type(TalkSubmitted.class),
                type(TalkAccepted.class),
                type(TalkRejected.class)
            )
            .tag(TALK_ID, command.talkId())
            .build();

        ProjectionResult<TalkState> talkResult = eventStore.project(talkQuery, talkStateProjector);
        TalkState talkState = talkResult.state();

        if (!talkState.exists()) {
            throw new TalkNotFoundException(command.talkId());
        }
        if (talkState.isAccepted()) {
            throw new TalkAlreadyAcceptedException(command.talkId());
        }
        if (!talkState.isPending()) {
            throw new TalkNotPendingException(command.talkId());
        }

        // 2. Project conference-wide accepted count
        //    This query has NO talk_id tag — it spans ALL talks in the conference
        Query conferenceQuery = QueryBuilder.builder()
            .events(type(TalkAccepted.class))
            .tag("conference_id", command.conferenceId())
            .build();

        ProjectionResult<ConferenceState> conferenceResult = eventStore.project(conferenceQuery, conferenceProjector);

        if (conferenceResult.state().acceptedCount() >= CONFERENCE_CAPACITY) {
            throw new ConferenceFullException(command.conferenceId());
        }

        // 3. Build the event
        TalkAccepted accepted = new TalkAccepted(command.talkId(), talkState.speakerId());

        AppendEvent appendEvent = AppendEvent.builder(type(TalkAccepted.class))
            .tag(TALK_ID, command.talkId())
            .tag(SPEAKER_ID, talkState.speakerId())
            .tag("conference_id", command.conferenceId())
            .data(accepted)
            .build();

        // 4. Non-commutative: scoped to the conference query.
        //    If any TalkAccepted event for this conference was written after
        //    conferenceResult.streamPosition(), the append will be rejected.
        return CommandDecision.NonCommutative.of(appendEvent, conferenceQuery, conferenceResult.streamPosition());
    }
}
```

### CommandExecutor — discovery and execution

`CommandExecutor` discovers all `@Component`-annotated `CommandHandler` beans at startup by inspecting their generic type parameter. When you call `executeCommand(command)`, it finds the matching handler, calls `handle`, stores the command as an audit record, and calls the correct append method based on the returned `CommandDecision` — all in a single database transaction.

```java
@Autowired
private CommandExecutor commandExecutor;

// Executes SubmitTalkCommandHandler.handle(...) in one transaction
commandExecutor.executeCommand(
    new SubmitTalkCommand("sub-1", "talk-1", "alice", "Event Sourcing in Practice")
);
```

The handler never needs to know about transactions, serialization, or audit trails. It is a pure function: state in, events out.

> **Key insight.** Handlers never call append methods directly. They return a `CommandDecision` and the executor calls the appropriate append method. This makes handlers pure and testable: the unit test just calls `handle` and inspects the returned events without a database.

---

## Part 5: Automations

**Why this matters.** When a talk is accepted, the speaker should receive a confirmation. You could put that logic in `AcceptTalkCommandHandler`, but that couples two concerns: enforcing consistency (the handler's job) and notifying an external party (a side effect). Automations decouple them. An automation is triggered asynchronously after an event is persisted, runs in its own transaction, and retries automatically on failure.

### The AutomationHandler interface

```java
// From com.crablet.automations.AutomationHandler
public interface AutomationHandler {
    String getAutomationName();
    void react(StoredEvent event, CommandExecutor commandExecutor);
}
```

The `react` method receives the raw `StoredEvent` and a `CommandExecutor`. It deserializes the event, derives a command, and executes it. The downstream command handler is responsible for being idempotent.

### TalkAcceptedAutomation

```java
import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.StoredEvent;
import com.crablet.automations.AutomationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TalkAcceptedAutomation implements AutomationHandler {

    private final ObjectMapper objectMapper;

    public TalkAcceptedAutomation(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAutomationName() {
        return "talk-accepted-confirmation";
    }

    @Override
    public void react(StoredEvent event, CommandExecutor commandExecutor) {
        try {
            TalkEvent talkEvent = objectMapper.readValue(event.data(), TalkEvent.class);
            if (talkEvent instanceof TalkAccepted accepted) {
                commandExecutor.executeCommand(
                    new SendConfirmationCommand(accepted.talkId(), accepted.speakerId())
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("TalkAcceptedAutomation failed", e);
        }
    }
}
```

### Declaring the subscription

`AutomationHandler` provides a `subscription()` default method — pass the handler as a bean parameter to avoid repeating the automation name:

```java
import static com.crablet.eventstore.EventType.type;

@Bean
public AutomationSubscription talkAcceptedConfirmationSubscription(TalkAcceptedAutomation handler) {
    return handler.subscription(type(TalkAccepted.class));
}
```

### Configuration

```properties
crablet.automations.enabled=true
crablet.automations.polling-interval-ms=1000
crablet.automations.batch-size=100
```

### Idempotency in the downstream handler

Reactions run with at-least-once semantics. The framework guarantees delivery but not exactly-once: if the process crashes after the command executes but before progress is saved, the reaction runs again. The `SendConfirmationCommandHandler` must protect against this:

```java
@Component
public class SendConfirmationCommandHandler implements IdempotentCommandHandler<SendConfirmationCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, SendConfirmationCommand command) {
        ConfirmationSent confirmationSent = new ConfirmationSent(
            command.talkId(), command.speakerId()
        );

        AppendEvent appendEvent = AppendEvent.builder(type(ConfirmationSent.class))
            .tag(TALK_ID, command.talkId())
            .data(confirmationSent)
            .build();

        // Idempotency check: fail if a ConfirmationSent for this talk_id already exists.
        // Running this command twice produces the same outcome: one confirmation event.
        return CommandDecision.Idempotent.of(appendEvent, type(ConfirmationSent.class), TALK_ID, command.talkId());
    }
}
```

> **Key insight.** Reactions decouple "what happened" from "what should happen next." They run asynchronously with at-least-once semantics. The downstream command handler must be idempotent — implement `IdempotentCommandHandler` to make running it twice produce the same outcome as running it once.

---

## Part 6: Views

**Why this matters.** Commands write to the event log. API queries need fast, filterable projections — not event replay on every HTTP request. Views are asynchronous materialized read models. An event processor polls the log and applies each event to a local database table. Queries read from that table with standard SQL.

### The database table

```sql
-- Flyway migration: V1__talks_view_schema.sql
CREATE TABLE talks_view (
    talk_id    VARCHAR(255)             PRIMARY KEY,
    speaker_id VARCHAR(255)             NOT NULL,
    title      VARCHAR(500)             NOT NULL,
    status     VARCHAR(50)              NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### TalksViewProjector

```java
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.views.AbstractTypedViewProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;

@Component
public class TalksViewProjector extends AbstractTypedViewProjector<TalkEvent> {

    public TalksViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "talks-view";
    }

    @Override
    protected Class<TalkEvent> getEventType() {
        return TalkEvent.class;
    }

    @Override
    protected boolean handleEvent(TalkEvent event, StoredEvent storedEvent, JdbcTemplate jdbc) {
        return switch (event) {
            case TalkSubmitted submitted -> {
                // ON CONFLICT ensures idempotency: if this event is processed twice,
                // the second write is a no-op (same data, same primary key).
                jdbc.update(
                    """
                    INSERT INTO talks_view (talk_id, speaker_id, title, status, updated_at)
                    VALUES (?, ?, ?, 'PENDING', ?)
                    ON CONFLICT (talk_id) DO UPDATE SET
                        title      = EXCLUDED.title,
                        status     = 'PENDING',
                        updated_at = EXCLUDED.updated_at
                    """,
                    submitted.talkId(),
                    submitted.speakerId(),
                    submitted.title(),
                    Timestamp.from(clockProvider.now())
                );
                yield true;
            }
            case TalkAccepted accepted -> {
                jdbc.update(
                    "UPDATE talks_view SET status = 'ACCEPTED', updated_at = ? WHERE talk_id = ?",
                    Timestamp.from(clockProvider.now()),
                    accepted.talkId()
                );
                yield true;
            }
            case TalkRejected rejected -> {
                jdbc.update(
                    "UPDATE talks_view SET status = 'REJECTED', updated_at = ? WHERE talk_id = ?",
                    Timestamp.from(clockProvider.now()),
                    rejected.talkId()
                );
                yield true;
            }
        };
    }
}
```

The `clockProvider` field is available from the base class. Each `handleEvent` call is wrapped in a transaction by the base class — if the SQL fails, the event will be retried.

### Declaring the subscription

`ViewProjector` provides a `subscription()` default method — pass the projector as a bean parameter to avoid repeating the view name:

```java
import static com.crablet.eventstore.EventType.type;

@Bean
public ViewSubscription talksViewSubscription(TalksViewProjector projector) {
    return projector.subscription(
        type(TalkSubmitted.class),
        type(TalkAccepted.class),
        type(TalkRejected.class)
    );
}
```

For subscriptions that need tag filtering, use `ViewSubscription.builder(projector.getViewName())` directly.


### Configuration

```properties
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100
```

### Reading from the view

```java
// A regular Spring JDBC query — no event replay, no projectors
List<TalkSummary> talks = jdbcTemplate.query(
    "SELECT talk_id, speaker_id, title, status FROM talks_view ORDER BY talk_id",
    (rs, row) -> new TalkSummary(
        rs.getString("talk_id"),
        rs.getString("speaker_id"),
        rs.getString("title"),
        TalkStatus.valueOf(rs.getString("status"))
    )
);
```

> **Key insight.** Views are eventually consistent. A talk accepted one second ago may not appear in `talks_view` yet. Never read from a view inside a command handler — always project from the event store. Views are for read-side queries that can tolerate a few seconds of lag.

---

## Part 7: Outbox

**Why this matters.** Views write to a local database table in the same PostgreSQL instance. When you need to notify external systems — send an email, publish to Kafka, call a webhook — you need the Outbox pattern. The event is captured in the same database transaction as the command, guaranteeing that the external notification is never lost even if the process crashes or the external system is unavailable.

### Reactions vs Outbox

| | Reactions | Outbox |
|--|-----------|--------|
| **What it does** | Executes more commands | Publishes to external systems |
| **Target** | Internal (same database) | External (Kafka, HTTP, email, etc.) |
| **Output** | New events in the event log | Calls to external APIs |
| **Idempotency** | Via downstream command handler | Via publisher implementation |
| **Use when** | You want to chain commands | You want to integrate with other systems |

Both patterns run with at-least-once semantics. Both use leader election so that only one application instance processes events at a time.

### The OutboxPublisher interface

```java
// From com.crablet.outbox.OutboxPublisher
public interface OutboxPublisher {
    String getName();
    void publishBatch(List<StoredEvent> events) throws PublishException;
    boolean isHealthy();
}
```

### EmailNotificationPublisher

```java
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailNotificationPublisher implements OutboxPublisher {

    private final ObjectMapper objectMapper;
    private final EmailService emailService; // your email client

    public EmailNotificationPublisher(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @Override
    public String getName() {
        return "email-notifications";
    }

    @Override
    public void publishBatch(List<StoredEvent> events) throws PublishException {
        for (StoredEvent event : events) {
            try {
                TalkEvent talkEvent = objectMapper.readValue(event.data(), TalkEvent.class);
                if (talkEvent instanceof TalkAccepted accepted) {
                    emailService.sendAcceptanceEmail(accepted.speakerId(), accepted.talkId());
                }
            } catch (Exception e) {
                throw new PublishException("Failed to publish event " + event.position(), e);
            }
        }
    }

    @Override
    public boolean isHealthy() {
        return emailService.isConnected();
    }
}
```

### Configuring outbox topics via application properties

The outbox uses a topic-based routing model. Topics are configured in `application.properties`. The publisher name declared in the topic must match `getName()` on the publisher bean.

```properties
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.batch-size=100

# Topic: route TalkAccepted events to the email-notifications publisher
crablet.outbox.topics.topics.talk-accepted-emails.publishers=email-notifications
```

The outbox event fetcher reads from the event log and routes events to the appropriate publishers based on topic configuration. If the email service is down, events remain in the pending position and are retried on the next polling cycle.

> **Key insight.** The outbox is transactionally safe: the event exists in the PostgreSQL log before the publisher ever runs. If your email service is down for an hour, no events are lost — they accumulate and are processed when the service recovers.

---

## Part 8: Testing

**Why this matters.** The framework is built for two distinct test layers. Collapsing them into one produces tests that are either too slow to run on every commit, or too shallow to catch real bugs. Understanding when to use each layer is as important as knowing the APIs.

### Layer 1: Domain tests — fast, in-memory, no database

Domain tests call `handler.handle(eventStore, command)` directly with `InMemoryEventStore`. They test business rules: does the right exception get thrown? Is the right event generated? Are the field values correct?

`InMemoryEventStore` stores Java objects directly — no JSON serialization. It applies real `StateProjector` logic. It does **not** enforce DCB concurrency checks: all appends succeed regardless of the condition. This makes it fast (under 10 ms per test) and makes it unsuitable for testing concurrency.

Base class: `AbstractHandlerUnitTest` from `com.crablet.command.handlers.unit`.

#### Happy path

```java
import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.talks.TalkTags.TALK_ID;
import static com.crablet.examples.talks.TalkTags.SPEAKER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptTalkCommandHandlerTest extends AbstractHandlerUnitTest {

    private AcceptTalkCommandHandler handler;

    @BeforeEach
    protected void setUp() {
        super.setUp(); // initializes this.eventStore (InMemoryEventStore)
        handler = new AcceptTalkCommandHandler();
    }

    @Test
    void givenPendingTalk_whenAccepting_thenTalkAcceptedEventGenerated() {
        // Given: a submitted talk exists
        var submitted = new TalkSubmitted("talk-1", "alice", "Event Sourcing in Practice");
        given().event(type(TalkSubmitted.class), builder -> builder
            .data(submitted)
            .tag(TALK_ID, submitted.talkId())
            .tag(SPEAKER_ID, submitted.speakerId())
        );

        // When
        List<Object> events = when(handler, new AcceptTalkCommand("conf-1", "talk-1"));

        // Then
        then(events, TalkAccepted.class, accepted -> {
            assertThat(accepted.talkId()).isEqualTo("talk-1");
            assertThat(accepted.speakerId()).isEqualTo("alice");
        });
    }
```

#### Error path: talk not found

```java
    @Test
    void givenNoTalk_whenAccepting_thenTalkNotFoundExceptionThrown() {
        // No events seeded — talk does not exist

        assertThatThrownBy(() -> when(handler, new AcceptTalkCommand("conf-1", "talk-99")))
            .isInstanceOf(TalkNotFoundException.class);
    }
```

#### Error path: conference full

```java
    @Test
    void givenConferenceAtCapacity_whenAcceptingAnotherTalk_thenConferenceFullExceptionThrown() {
        // Given: two talks already accepted (conference capacity = 2)
        var s1 = new TalkSubmitted("talk-1", "alice", "Talk A");
        var a1 = new TalkAccepted("talk-1", "alice");
        var s2 = new TalkSubmitted("talk-2", "bob", "Talk B");
        var a2 = new TalkAccepted("talk-2", "bob");
        var s3 = new TalkSubmitted("talk-3", "carol", "Talk C");
        given()
            .event(type(TalkSubmitted.class), builder -> builder
                .data(s1).tag(TALK_ID, s1.talkId()).tag(SPEAKER_ID, s1.speakerId()).tag("conference_id", "conf-1")
            )
            .event(type(TalkAccepted.class), builder -> builder
                .data(a1).tag(TALK_ID, a1.talkId()).tag(SPEAKER_ID, a1.speakerId()).tag("conference_id", "conf-1")
            )
            .event(type(TalkSubmitted.class), builder -> builder
                .data(s2).tag(TALK_ID, s2.talkId()).tag(SPEAKER_ID, s2.speakerId()).tag("conference_id", "conf-1")
            )
            .event(type(TalkAccepted.class), builder -> builder
                .data(a2).tag(TALK_ID, a2.talkId()).tag(SPEAKER_ID, a2.speakerId()).tag("conference_id", "conf-1")
            )
            .event(type(TalkSubmitted.class), builder -> builder
                .data(s3).tag(TALK_ID, s3.talkId()).tag(SPEAKER_ID, s3.speakerId()).tag("conference_id", "conf-1")
            );

        // When/Then: accepting a third talk fails
        assertThatThrownBy(() -> when(handler, new AcceptTalkCommand("conf-1", "talk-3")))
            .isInstanceOf(ConferenceFullException.class);
    }
}
```

### Layer 2: Integration (E2E) tests — real database, real concurrency

Integration tests use a real PostgreSQL database (started by Testcontainers) and a full Spring context. They test things that cannot be tested in memory: DCB conflict detection, database constraints, transaction boundaries, and full pipeline flows (submit -> accept -> view updated).

Base class: `AbstractCrabletTest` from `com.crablet.test`. Subclasses must add `@SpringBootTest` with their test application class.

#### Full pipeline: submit, accept, query view

```java
import com.crablet.test.AbstractCrabletTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TalkLifecycleIntegrationTest extends AbstractCrabletTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Test
    void givenSubmittedTalk_whenAccepted_thenTalkIsAccepted() {
        String talkId  = "talk-" + System.currentTimeMillis();
        String subId   = "sub-"  + System.currentTimeMillis();

        // Submit
        commandExecutor.executeCommand(
            new SubmitTalkCommand(subId, talkId, "alice", "DCB in Practice")
        );

        // Accept
        commandExecutor.executeCommand(
            new AcceptTalkCommand("conf-1", talkId)
        );

        // Verify via event store projection
        var query = QueryBuilder.builder()
            .events(type(TalkAccepted.class))
            .tag(TALK_ID, talkId)
            .build();

        ProjectionResult<TalkState> result = eventStore.project(query, new TalkStateProjector());

        assertThat(result.state().isAccepted()).isTrue();
    }
}
```

#### DCB race condition: exactly one acceptance when conference is full

This test cannot be written as a domain test because `InMemoryEventStore` skips concurrency checks by design. It requires a real PostgreSQL instance.

```java
    @Test
    void givenConferenceWithOneSlot_whenTwoAcceptsRunConcurrently_thenOnlyOneSucceeds()
            throws InterruptedException {

        // Setup: submit two talks, accept the first to fill one of two slots
        String talkA = "talk-A-" + System.currentTimeMillis();
        String talkB = "talk-B-" + System.currentTimeMillis();
        String talkC = "talk-C-" + System.currentTimeMillis();

        commandExecutor.executeCommand(new SubmitTalkCommand("sub-a", talkA, "alice", "Talk A"));
        commandExecutor.executeCommand(new SubmitTalkCommand("sub-b", talkB, "bob",   "Talk B"));
        commandExecutor.executeCommand(new SubmitTalkCommand("sub-c", talkC, "carol", "Talk C"));
        commandExecutor.executeCommand(new AcceptTalkCommand("conf-1", talkA));

        // Both threads try to accept the last slot simultaneously
        CountDownLatch ready  = new CountDownLatch(2);
        CountDownLatch start  = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        Runnable acceptB = () -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { commandExecutor.executeCommand(new AcceptTalkCommand("conf-1", talkB)); }
            catch (Exception e) { errors.add(e); }
        };

        Runnable acceptC = () -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { commandExecutor.executeCommand(new AcceptTalkCommand("conf-1", talkC)); }
            catch (Exception e) { errors.add(e); }
        };

        Thread threadB = new Thread(acceptB);
        Thread threadC = new Thread(acceptC);
        threadB.start();
        threadC.start();
        ready.await();
        start.countDown(); // release both threads simultaneously
        threadB.join();
        threadC.join();

        // Exactly one exception: the loser gets ConcurrencyException
        assertThat(errors).hasSize(1);

        // Exactly two accepted talks in the event store (the pre-seeded one + one winner)
        var conferenceQuery = QueryBuilder.builder()
            .events(type(TalkAccepted.class))
            .tag("conference_id", "conf-1")
            .build();

        ProjectionResult<ConferenceState> conference = eventStore.project(conferenceQuery, new ConferenceStateProjector());

        assertThat(conference.state().acceptedCount()).isEqualTo(2);
    }
```

### The rule of thumb

Use domain tests (`AbstractHandlerUnitTest`) for:
- Business rule validation (wrong status, insufficient capacity)
- Event generation (correct fields, correct types)
- State transitions (PENDING -> ACCEPTED -> not re-acceptable)

Use integration tests (`AbstractCrabletTest`) for:
- DCB conflict detection — `InMemoryEventStore` does not enforce concurrency checks
- Database constraint verification
- Full pipeline flows (command -> event -> view updated)
- Reaction chains (event triggers command triggers event)

DCB violations are only testable at the integration level. This is intentional: the unit test layer tests business logic, and that should be fast and deterministic. Concurrency is a property of the real database, not of the in-memory mock.

---

## What to explore next

With all eight parts covered, you have seen every major framework feature:

- **EventStore** (`appendCommutative`, `appendNonCommutative`, `appendIdempotent`, `project`) — the append-only log and state reconstruction
- **CommandDecision** — three variants for three situations (Commutative, Idempotent, NonCommutative)
- **CommandHandler + CommandExecutor** — transactional command execution with automatic audit
- **AutomationHandler + AutomationSubscription** — async command chains with at-least-once delivery
- **AbstractTypedViewProjector + ViewSubscription** — materialized read models
- **OutboxPublisher** — reliable external event delivery
- **AbstractHandlerUnitTest / AbstractCrabletTest** — two-layer test strategy

**Reference material:**
- Complete working application: `wallet-example-app/`
- DCB patterns explained: `crablet-eventstore/docs/DCB_AND_CRABLET.md`
- Command patterns reference: `crablet-eventstore/docs/COMMAND_PATTERNS.md`
- Closing the books (period segmentation): `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`
- Testing strategy: `crablet-eventstore/TESTING.md`
