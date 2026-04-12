package com.crablet.docs.samples.tutorial;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.crablet.eventstore.query.StateProjector;

import java.util.List;

import static com.crablet.eventstore.EventType.type;

final class Tutorial01EventStoreBasicsSample {

    // docs:begin tutorial01-main
    sealed interface TalkEvent permits TalkSubmitted, TalkAccepted, TalkRejected {}

    record TalkSubmitted(String talkId, String speakerId, String title) implements TalkEvent {}
    record TalkAccepted(String talkId, String speakerId) implements TalkEvent {}
    record TalkRejected(String talkId, String speakerId, String reason) implements TalkEvent {}

    enum TalkStatus { PENDING, ACCEPTED, REJECTED }

    record TalkState(String talkId, String speakerId, TalkStatus status, boolean exists) {
        static TalkState empty() {
            return new TalkState(null, null, null, false);
        }
    }

    static void sample(EventStore eventStore) {
        TalkSubmitted submitted = new TalkSubmitted("talk-1", "alice", "Event Sourcing in Practice");

        AppendEvent appendEvent = AppendEvent.builder(type(TalkSubmitted.class))
                .tag("talk_id", submitted.talkId())
                .tag("speaker_id", submitted.speakerId())
                .data(submitted)
                .build();

        eventStore.appendCommutative(List.of(appendEvent));

        Query query = QueryBuilder.builder()
                .events(type(TalkSubmitted.class))
                .tag("talk_id", "talk-1")
                .build();

        boolean exists = eventStore.exists(query);
        ProjectionResult<TalkState> result = eventStore.project(query, new TalkStateProjector());

        if (exists && result.state().exists()) {
            result.streamPosition();
        }
    }

    static final class TalkStateProjector implements StateProjector<TalkState> {
        @Override
        public List<String> getEventTypes() {
            return List.of(type(TalkSubmitted.class), type(TalkAccepted.class), type(TalkRejected.class));
        }

        @Override
        public TalkState getInitialState() {
            return TalkState.empty();
        }

        @Override
        public TalkState transition(TalkState state, StoredEvent event, EventDeserializer deserializer) {
            TalkEvent talkEvent = deserializer.deserialize(event, TalkEvent.class);
            return switch (talkEvent) {
                case TalkSubmitted s -> new TalkState(s.talkId(), s.speakerId(), TalkStatus.PENDING, true);
                case TalkAccepted a -> new TalkState(state.talkId(), state.speakerId(), TalkStatus.ACCEPTED, true);
                case TalkRejected r -> new TalkState(state.talkId(), state.speakerId(), TalkStatus.REJECTED, true);
            };
        }
    }
    // docs:end tutorial01-main
}
