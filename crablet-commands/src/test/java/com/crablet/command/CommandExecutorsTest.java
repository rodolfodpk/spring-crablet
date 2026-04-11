package com.crablet.command;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CommandExecutors Factory Tests")
class CommandExecutorsTest {

    @Test
    @DisplayName("create should return a CommandExecutor")
    void create_ShouldReturnCommandExecutor() {
        EventStore eventStore = new NoOpEventStore();
        EventStoreConfig config = new EventStoreConfig();
        ClockProvider clock = new ClockProviderImpl();
        ObjectMapper objectMapper = JsonMapper.builder().build();
        ApplicationEventPublisher eventPublisher = event -> { };

        CommandExecutor executor = CommandExecutors.create(
                eventStore,
                Collections.emptyList(),
                config,
                clock,
                objectMapper,
                eventPublisher
        );

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(CommandExecutor.class);
    }

    private static final class NoOpEventStore implements EventStore {
        @Override
        public String appendCommutative(List<AppendEvent> events) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String appendNonCommutative(List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String appendIdempotent(List<AppendEvent> events, String eventType, String tagKey, String tagValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String appendIdempotent(List<AppendEvent> events, Query idempotencyQuery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> ProjectionResult<T> project(Query query, StreamPosition after, Class<T> stateType,
                                               List<StateProjector<T>> projectors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T executeInTransaction(Function<EventStore, T> operation) {
            throw new UnsupportedOperationException();
        }
    }
}
