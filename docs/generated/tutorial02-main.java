    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "commandType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SubmitTalkCommand.class, name = "submit_talk")
    })
    interface TalkCommand {
    }

    record TalkSubmitted(String talkId, String speakerId, String title) {
    }

    record SubmitTalkCommand(
            String talkId,
            String speakerId,
            String title,
            String submissionId
    ) implements TalkCommand {
    }

    static final class SubmitTalkCommandHandler implements IdempotentCommandHandler<SubmitTalkCommand> {
        @Override
        public CommandDecision.Idempotent decide(EventStore eventStore, SubmitTalkCommand command) {
            TalkSubmitted event = new TalkSubmitted(command.talkId(), command.speakerId(), command.title());

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

    static ExecutionResult sample(CommandExecutor commandExecutor) {
        return commandExecutor.execute(
                new SubmitTalkCommand("talk-1", "alice", "Event Sourcing in Practice", "submission-1")
        );
    }
