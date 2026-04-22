package com.crablet.views.internal;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.crablet.views.ViewProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ViewEventHandler.
 * Tests delegation to ViewProjector implementations, registration, and error handling.
 */
@DisplayName("ViewEventHandler Unit Tests")
class ViewEventHandlerTest {

    @Test
    @DisplayName("Should register projectors by view name")
    void shouldRegisterProjectors_ByViewName() throws Exception {
        // Given
        TestProjector projector1 = new TestProjector("wallet-view");
        TestProjector projector2 = new TestProjector("order-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector1, projector2), e -> {});

        // When
        List<StoredEvent> events = createTestEvents();
        handler.handle("wallet-view", events);

        // Then
        assertThat(projector1.handledCount).isEqualTo(1);
        assertThat(projector2.handledCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should delegate to correct projector based on view name")
    void shouldDelegateToCorrectProjector_BasedOnViewName() throws Exception {
        // Given
        TestProjector walletProjector = new TestProjector("wallet-view");
        TestProjector orderProjector = new TestProjector("order-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(walletProjector, orderProjector), e -> {});

        List<StoredEvent> events = createTestEvents();

        // When
        int result1 = handler.handle("wallet-view", events);
        int result2 = handler.handle("order-view", events);

        // Then
        assertThat(result1).isEqualTo(events.size());
        assertThat(result2).isEqualTo(events.size());
        assertThat(walletProjector.handledCount).isEqualTo(1);
        assertThat(orderProjector.handledCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return 0 when no projector registered for view")
    void shouldReturnZero_WhenNoProjectorRegisteredForView() throws Exception {
        // Given
        ViewEventHandler handler = new ViewEventHandler(List.of(), e -> {});
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("non-existent-view", events);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should propagate exceptions from projector")
    void shouldPropagateExceptions_FromProjector() {
        // Given
        FailingProjector projector = new FailingProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector), e -> {});
        List<StoredEvent> events = createTestEvents();

        // When & Then
        assertThatThrownBy(() -> handler.handle("wallet-view", events))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");
    }

    @Test
    @DisplayName("Should handle multiple projectors with same view name (last one wins)")
    void shouldHandleMultipleProjectors_WithSameViewName() throws Exception {
        // Given
        TestProjector projector1 = new TestProjector("wallet-view");
        TestProjector projector2 = new TestProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector1, projector2), e -> {});

        List<StoredEvent> events = createTestEvents();

        // When
        handler.handle("wallet-view", events);

        // Then - Last registered projector should be used
        assertThat(projector2.handledCount).isEqualTo(1);
        assertThat(projector1.handledCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty projector list")
    void shouldHandleEmptyProjectorList() throws Exception {
        // Given
        ViewEventHandler handler = new ViewEventHandler(List.of(), e -> {});
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("any-view", events);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty events list")
    void shouldHandleEmptyEventsList() throws Exception {
        // Given
        TestProjector projector = new TestProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector), e -> {});

        // When
        int result = handler.handle("wallet-view", List.of());

        // Then
        assertThat(result).isEqualTo(0);
        assertThat(projector.handledCount).isEqualTo(1);
        assertThat(projector.lastEventsReceived).isEmpty();
    }

    @Test
    @DisplayName("Should pass events to projector correctly")
    void shouldPassEventsToProjector_Correctly() throws Exception {
        // Given
        TestProjector projector = new TestProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector), e -> {});
        List<StoredEvent> events = createTestEvents();

        // When
        handler.handle("wallet-view", events);

        // Then
        assertThat(projector.lastEventsReceived).isSameAs(events);
    }

    @Test
    @DisplayName("Should return count from projector")
    void shouldReturnCount_FromProjector() throws Exception {
        // Given
        CountingProjector projector = new CountingProjector("wallet-view", 5);
        ViewEventHandler handler = new ViewEventHandler(List.of(projector), e -> {});
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("wallet-view", events);

        // Then
        assertThat(result).isEqualTo(5);
    }

    // Test implementations

    static class TestProjector implements ViewProjector {
        private final String viewName;
        int handledCount = 0;
        List<StoredEvent> lastEventsReceived = new ArrayList<>();

        TestProjector(String viewName) {
            this.viewName = viewName;
        }

        @Override
        public String getViewName() {
            return viewName;
        }

        @Override
        public int handle(String viewName, List<StoredEvent> events) throws Exception {
            handledCount++;
            lastEventsReceived = events;
            return events.size();
        }
    }

    static class FailingProjector implements ViewProjector {
        private final String viewName;

        FailingProjector(String viewName) {
            this.viewName = viewName;
        }

        @Override
        public String getViewName() {
            return viewName;
        }

        @Override
        public int handle(String viewName, List<StoredEvent> events) throws Exception {
            throw new RuntimeException("Test exception");
        }
    }

    static class CountingProjector implements ViewProjector {
        private final String viewName;
        private final int returnCount;

        CountingProjector(String viewName, int returnCount) {
            this.viewName = viewName;
            this.returnCount = returnCount;
        }

        @Override
        public String getViewName() {
            return viewName;
        }

        @Override
        public int handle(String viewName, List<StoredEvent> events) throws Exception {
            return returnCount;
        }
    }

    private List<StoredEvent> createTestEvents() {
        List<StoredEvent> events = new ArrayList<>();
        events.add(new StoredEvent(
            "WalletOpened",
            List.of(new Tag("wallet_id", "wallet-1")),
            "{\"walletId\":\"wallet-1\"}".getBytes(),
            "tx-1",
            1L,
            Instant.now()
        ));
        events.add(new StoredEvent(
            "DepositMade",
            List.of(new Tag("wallet_id", "wallet-1")),
            "{\"amount\":100}".getBytes(),
            "tx-2",
            2L,
            Instant.now()
        ));
        return events;
    }
}
