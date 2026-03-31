package com.crablet.eventstore.query;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.test.InMemoryEventStore;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StateProjector}, specifically the {@link StateProjector#exists} static factory.
 */
@DisplayName("StateProjector Unit Tests")
class StateProjectorTest {

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

        boolean exists = eventStore.project(
            query, Cursor.zero(), StateProjector.exists("WalletOpened")
        ).state();

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("exists() returns true after matching event is appended")
    void existsReturnsTrueAfterMatchingEvent() {
        // Seed a WalletOpened event
        eventStore.appendIf(List.of(
            AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ), AppendCondition.empty());

        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "w1");

        boolean exists = eventStore.project(
            query, Cursor.zero(), StateProjector.exists("WalletOpened")
        ).state();

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("exists() returns false when event type does not match")
    void existsReturnsFalseWhenEventTypeDoesNotMatch() {
        // Seed a DepositMade event (not WalletOpened)
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ), AppendCondition.empty());

        // Query for WalletOpened — no such event exists
        Query query = Query.forEventAndTag("WalletOpened", "wallet_id", "w1");

        boolean exists = eventStore.project(
            query, Cursor.zero(), StateProjector.exists("WalletOpened")
        ).state();

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("exists() with multiple event types — returns true when any type matches")
    void existsReturnsTrueForAnyMatchingType() {
        eventStore.appendIf(List.of(
            AppendEvent.builder("DepositMade")
                .tag("wallet_id", "w1")
                .data("{}")
                .build()
        ), AppendCondition.empty());

        // Query without tag filtering
        Query query = Query.forEventAndTag("DepositMade", "wallet_id", "w1");

        boolean exists = eventStore.project(
            query, Cursor.zero(), StateProjector.exists("WalletOpened", "DepositMade")
        ).state();

        assertThat(exists).isTrue();
    }
}
