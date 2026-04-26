    record TalkAccepted(String talkId, String speakerId) {
    }

    record ConferenceState(int acceptedCount) {
    }

    static final class ConferenceStateProjector implements StateProjector<ConferenceState> {
        @Override
        public List<String> getEventTypes() {
            return List.of(type(TalkAccepted.class));
        }

        @Override
        public ConferenceState getInitialState() {
            return new ConferenceState(0);
        }

        @Override
        public ConferenceState transition(ConferenceState state, StoredEvent event, EventDeserializer deserializer) {
            return new ConferenceState(state.acceptedCount() + 1);
        }
    }

    static void sample(EventStore eventStore, String conferenceId, String talkId, String speakerId) {
        Query conferenceQuery = QueryBuilder.builder()
                .events(type(TalkAccepted.class))
                .tag("conference_id", conferenceId)
                .build();

        ProjectionResult<ConferenceState> result =
                eventStore.project(conferenceQuery, new ConferenceStateProjector());

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
    }
