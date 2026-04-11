# Part 1: Event Store Basics

This tutorial introduces `crablet-eventstore` only.

You will learn:

- how to append your first event
- how to tag events
- how to project state back from the event log

## Domain

```java
public sealed interface TalkEvent permits TalkSubmitted, TalkAccepted, TalkRejected {}

public record TalkSubmitted(String talkId, String speakerId, String title) implements TalkEvent {}
public record TalkAccepted(String talkId, String speakerId) implements TalkEvent {}
public record TalkRejected(String talkId, String speakerId, String reason) implements TalkEvent {}
```

## Append An Event

```java
TalkSubmitted submitted = new TalkSubmitted("talk-1", "alice", "Event Sourcing in Practice");

AppendEvent appendEvent = AppendEvent.builder(type(TalkSubmitted.class))
    .tag("talk_id", submitted.talkId())
    .tag("speaker_id", submitted.speakerId())
    .data(submitted)
    .build();

eventStore.appendCommutative(List.of(appendEvent));
```

## Read It Back

```java
var query = QueryBuilder.builder()
    .events(type(TalkSubmitted.class))
    .tag("talk_id", "talk-1")
    .build();

boolean exists = eventStore.exists(query);
```

## Project Full State

```java
ProjectionResult<TalkState> result =
    eventStore.project(query, new TalkStateProjector());
```

`ProjectionResult` gives you:

- the projected state
- the `StreamPosition` of the last event you read

That stream position becomes important in Part 3.

## Next

Continue with [Part 2: Commands](02-commands.md).
