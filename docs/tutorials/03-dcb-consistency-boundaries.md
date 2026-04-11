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
var conferenceQuery = QueryBuilder.builder()
    .events(type(TalkAccepted.class))
    .tag("conference_id", conferenceId)
    .build();

ProjectionResult<ConferenceState> result =
    eventStore.project(conferenceQuery, conferenceProjector);
```

## Protected Write

```java
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
