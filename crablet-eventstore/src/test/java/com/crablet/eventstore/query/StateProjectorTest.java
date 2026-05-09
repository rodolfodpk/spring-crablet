package com.crablet.eventstore.query;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StoredEvent;
import com.crablet.test.InMemoryEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StateProjector}: the {@link StateProjector#exists} factory
 * and the {@link StateProjector.Builder} fluent API.
 */
@DisplayName("StateProjector Unit Tests")
class StateProjectorTest {

    private record ThingHappened(String value) {}
    private record CountIncremented(int delta) {}

    private InMemoryEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
    }

    // ===== StateProjector.exists() interface contract =====

    @Test
    @DisplayName("exists() initial state should be false")
    void existsInitialStateIsFalse() {
        StateProjector<Boolean> projector = StateProjector.exists("WalletOpened");
        assertThat(projector.getInitialState()).isFalse();
    }

    @Test
    @DisplayName("exists() getEventTypes() should return the supplied types")
    void existsReturnsSuppliedEventTypes() {
        StateProjector<Boolean> projector = StateProjector.exists("WalletOpened", "DepositMade");
        assertThat(projector.getEventTypes()).containsExactly("WalletOpened", "DepositMade");
    }

    @Test
    @DisplayName("exists() transition() should always return true regardless of event content")
    void existsTransitionAlwaysReturnsTrue() {
        StateProjector<Boolean> projector = StateProjector.exists("WalletOpened");
        StoredEvent dummyEvent = new StoredEvent("WalletOpened", List.of(), new byte[0], "tx1", 1L, Instant.now());
        EventDeserializer noop = new EventDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <E> E deserialize(StoredEvent event, Class<E> type) { return (E) new Object(); }
        };
        boolean result = projector.transition(false, dummyEvent, noop);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("exists() transition() stays true once true")
    void existsTransitionStaysTrueOnceSeen() {
        StateProjector<Boolean> projector = StateProjector.exists("WalletOpened");
        StoredEvent dummyEvent = new StoredEvent("WalletOpened", List.of(), new byte[0], "tx1", 1L, Instant.now());
        EventDeserializer noop = new EventDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <E> E deserialize(StoredEvent event, Class<E> type) { return (E) new Object(); }
        };
        boolean result = projector.transition(true, dummyEvent, noop);
        assertThat(result).isTrue();
    }

    // ===== Integration with InMemoryEventStore =====

    @Test
    @DisplayName("exists() returns false when no matching events in store")
    void existsReturnsFalseWhenNoEvents() {
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "w1");

        boolean exists = eventStore.exists(query);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("exists() returns true after matching event is appended")
    void existsReturnsTrueAfterMatchingEvent() {
        // Seed a WalletOpened event
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ));

        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "w1");

        boolean exists = eventStore.exists(query);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("exists() returns false when event type does not match")
    void existsReturnsFalseWhenEventTypeDoesNotMatch() {
        // Seed a DepositMade event (not WalletOpened)
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ));

        // Query for WalletOpened — no such event exists
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "w1");

        boolean exists = eventStore.exists(query);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("exists() returns true when any event matching the query exists")
    void existsReturnsTrueForAnyMatchingType() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ));

        Query query = Query.forEventAndTag("DepositMade", "wallet_id", "w1");

        boolean exists = eventStore.exists(query);

        assertThat(exists).isTrue();
    }

    // ===== StateProjector.Builder =====

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("getEventTypes() returns registered types in declaration order")
        void returnsEventTypesInDeclarationOrder() {
            StateProjector<String> projector = StateProjector.<String>builder("test-id", "")
                    .on(ThingHappened.class, (state, event) -> state)
                    .on(CountIncremented.class, (state, event) -> state)
                    .build();

            assertThat(projector.getEventTypes())
                    .containsExactly(type(ThingHappened.class), type(CountIncremented.class));
        }

        @Test
        @DisplayName("getId() returns the id passed to builder()")
        void returnsCustomId() {
            StateProjector<String> projector = StateProjector.<String>builder("my-custom-id", "")
                    .on(ThingHappened.class, (state, event) -> state)
                    .build();

            assertThat(projector.getId()).isEqualTo("my-custom-id");
        }

        @Test
        @DisplayName("getInitialState() returns the initial state passed to builder()")
        void returnsInitialState() {
            StateProjector<String> projector = StateProjector.<String>builder("id", "start")
                    .on(ThingHappened.class, (state, event) -> state)
                    .build();

            assertThat(projector.getInitialState()).isEqualTo("start");
        }

        @Test
        @DisplayName("transition() deserializes event and applies registered handler")
        void dispatchesRegisteredHandler() {
            StateProjector<String> projector = StateProjector.<String>builder("id", "")
                    .on(ThingHappened.class, (state, event) -> state + event.value())
                    .build();

            StoredEvent stored = new StoredEvent(
                    type(ThingHappened.class), List.of(), new byte[0], "tx1", 1L, Instant.now());
            EventDeserializer fakeDeserializer = new EventDeserializer() {
                @Override
                @SuppressWarnings("unchecked")
                public <E> E deserialize(StoredEvent event, Class<E> clazz) {
                    return (E) new ThingHappened("hello");
                }
            };

            assertThat(projector.transition("", stored, fakeDeserializer)).isEqualTo("hello");
        }

        @Test
        @DisplayName("transition() returns current state unchanged for unregistered event types")
        void returnsCurrentStateForUnregisteredEvent() {
            StateProjector<String> projector = StateProjector.<String>builder("id", "")
                    .on(ThingHappened.class, (state, event) -> state + event.value())
                    .build();

            StoredEvent stored = new StoredEvent(
                    type(CountIncremented.class), List.of(), new byte[0], "tx1", 1L, Instant.now());
            EventDeserializer shouldNotBeCalled = new EventDeserializer() {
                @Override
                public <E> E deserialize(StoredEvent event, Class<E> clazz) {
                    throw new AssertionError("deserializer must not be called for unregistered types");
                }
            };

            assertThat(projector.transition("unchanged", stored, shouldNotBeCalled)).isEqualTo("unchanged");
        }

        @Test
        @DisplayName("on() throws IllegalArgumentException immediately for duplicate event class")
        void throwsImmediatelyForDuplicateRegistration() {
            StateProjector.Builder<String> builder = StateProjector.<String>builder("id", "")
                    .on(ThingHappened.class, (state, event) -> state);

            assertThatThrownBy(() -> builder.on(ThingHappened.class, (state, event) -> state))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(type(ThingHappened.class));
        }

        @Test
        @DisplayName("on() throws IllegalStateException after build() has been called")
        void throwsAfterBuild() {
            StateProjector.Builder<String> builder = StateProjector.<String>builder("id", "")
                    .on(ThingHappened.class, (state, event) -> state);
            builder.build();

            assertThatThrownBy(() -> builder.on(CountIncremented.class, (state, event) -> state))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
