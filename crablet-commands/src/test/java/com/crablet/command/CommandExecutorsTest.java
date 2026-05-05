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

import org.jspecify.annotations.NonNull;

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
        public @NonNull String appendCommutative(@NonNull List<AppendEvent> events) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull String appendNonCommutative(
                @NonNull List<AppendEvent> events,
                @NonNull Query decisionModel,
                @NonNull StreamPosition streamPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull String appendIdempotent(
                @NonNull List<AppendEvent> events,
                @NonNull String eventType,
                @NonNull String tagKey,
                @NonNull String tagValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull String appendIdempotent(@NonNull List<AppendEvent> events, @NonNull Query idempotencyQuery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> @NonNull ProjectionResult<T> project(
                @NonNull Query query,
                @NonNull StreamPosition after,
                @NonNull Class<T> stateType,
                @NonNull List<StateProjector<T>> projectors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T executeInTransaction(@NonNull Function<@NonNull EventStore, T> operation) {
            throw new UnsupportedOperationException();
        }
    }
}
