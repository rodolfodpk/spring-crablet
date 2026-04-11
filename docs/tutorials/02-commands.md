# Part 2: Commands

This tutorial introduces `crablet-commands`.

You will learn:

- how command handlers wrap event-store writes
- how Crablet maps command types to different append semantics
- how to keep command logic transactional and explicit

## Why Use Commands

You can call `EventStore` directly, but `CommandHandler` gives you:

- automatic handler discovery
- one transactional execution path
- a clear separation between command intent and event persistence

## Example

```java
@Component
public class SubmitTalkCommandHandler implements IdempotentCommandHandler<SubmitTalkCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, SubmitTalkCommand command) {
        TalkSubmitted event = new TalkSubmitted(
            command.talkId(), command.speakerId(), command.title()
        );

        AppendEvent appendEvent = AppendEvent.builder(type(TalkSubmitted.class))
            .tag("talk_id", command.talkId())
            .tag("speaker_id", command.speakerId())
            .tag("submission_id", command.submissionId())
            .data(event)
            .build();

        return CommandDecision.Idempotent.of(
            appendEvent,
            type(TalkSubmitted.class),
            "submission_id",
            command.submissionId()
        );
    }
}
```

## What To Choose

- `CommutativeCommandHandler`: order does not matter
- `IdempotentCommandHandler`: entity creation or duplicate-prevention
- `NonCommutativeCommandHandler`: state-dependent decisions

## Next

Continue with [Part 3: DCB Consistency Boundaries](03-dcb-consistency-boundaries.md).
