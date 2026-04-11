# Part 3: DCB Consistency Boundaries

This tutorial stays in `crablet-eventstore` and introduces the core DCB idea.

You will learn:

- why a fixed aggregate stream is not enough for some invariants
- how to build a decision model with a query
- how `StreamPosition` protects state-dependent writes

## Example Constraint

The conference can accept at most `CAPACITY` talks.

Two organizers can both observe `acceptedCount == 1` and both accept another talk unless the write is tied to the read version.

## Decision Model

```java
public record ConferenceState(int acceptedCount) {}

public class ConferenceStateProjector implements StateProjector<ConferenceState> {

    @Override
    public List<String> getEventTypes() {
        return List.of(type(TalkAccepted.class));
    }

    @Override
    public ConferenceState getInitialState() {
        return new ConferenceState(0);
    }

    @Override
    public ConferenceState transition(
            ConferenceState state,
            StoredEvent event,
            EventDeserializer deserializer) {
        return new ConferenceState(state.acceptedCount() + 1);
    }
}

var conferenceQuery = QueryBuilder.builder()
    .events(type(TalkAccepted.class))
    .tag("conference_id", conferenceId)
    .build();

ProjectionResult<ConferenceState> result =
    eventStore.project(conferenceQuery, new ConferenceStateProjector());
```

## Protected Write

```java
AppendEvent acceptTalkEvent = AppendEvent.builder(type(TalkAccepted.class))
    .tag("conference_id", conferenceId)
    .tag("talk_id", talkId)
    .tag("speaker_id", speakerId)
    .data(new TalkAccepted(talkId, speakerId))
    .build();

eventStore.appendNonCommutative(
    List.of(acceptTalkEvent),
    conferenceQuery,
    result.streamPosition()
);
```

If another matching event was appended after `result.streamPosition()`, Crablet throws `ConcurrencyException` and the caller retries from a fresh projection.

That is the DCB boundary:

- read the exact events that matter for the decision
- write only if nothing relevant changed meanwhile

## Next

Continue with [Part 4: Views](04-views.md).
