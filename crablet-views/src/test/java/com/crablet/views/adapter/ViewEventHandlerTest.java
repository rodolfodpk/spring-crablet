package com.crablet.views.adapter;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.crablet.views.ViewProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

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
        List<ViewProjector> projectors = List.of(projector1, projector2);

        // When
        ViewEventHandler handler = new ViewEventHandler(projectors);

        // Then - Verify registration by calling handle
        List<StoredEvent> events = createTestEvents();
        handler.handle("wallet-view", events, null);
        assertThat(projector1.handledCount).isEqualTo(1);
        assertThat(projector2.handledCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should delegate to correct projector based on view name")
    void shouldDelegateToCorrectProjector_BasedOnViewName() throws Exception {
        // Given
        TestProjector walletProjector = new TestProjector("wallet-view");
        TestProjector orderProjector = new TestProjector("order-view");
        List<ViewProjector> projectors = List.of(walletProjector, orderProjector);
        ViewEventHandler handler = new ViewEventHandler(projectors);

        List<StoredEvent> events = createTestEvents();

        // When
        int result1 = handler.handle("wallet-view", events, null);
        int result2 = handler.handle("order-view", events, null);

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
        ViewEventHandler handler = new ViewEventHandler(List.of());
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("non-existent-view", events, null);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should propagate exceptions from projector")
    void shouldPropagateExceptions_FromProjector() {
        // Given
        FailingProjector projector = new FailingProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector));
        List<StoredEvent> events = createTestEvents();

        // When & Then
        assertThatThrownBy(() -> handler.handle("wallet-view", events, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");
    }

    @Test
    @DisplayName("Should pass writeDataSource to projector")
    void shouldPassWriteDataSource_ToProjector() throws Exception {
        // Given
        DataSourceCapturingProjector projector = new DataSourceCapturingProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector));
        List<StoredEvent> events = createTestEvents();
        DataSource testDataSource = new TestDataSource();

        // When
        handler.handle("wallet-view", events, testDataSource);

        // Then
        assertThat(projector.receivedDataSource).isSameAs(testDataSource);
    }

    @Test
    @DisplayName("Should handle multiple projectors with same view name (last one wins)")
    void shouldHandleMultipleProjectors_WithSameViewName() throws Exception {
        // Given
        TestProjector projector1 = new TestProjector("wallet-view");
        TestProjector projector2 = new TestProjector("wallet-view");
        List<ViewProjector> projectors = List.of(projector1, projector2);
        ViewEventHandler handler = new ViewEventHandler(projectors);

        List<StoredEvent> events = createTestEvents();

        // When
        handler.handle("wallet-view", events, null);

        // Then - Last registered projector should be used
        assertThat(projector2.handledCount).isEqualTo(1);
        assertThat(projector1.handledCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty projector list")
    void shouldHandleEmptyProjectorList() throws Exception {
        // Given
        ViewEventHandler handler = new ViewEventHandler(List.of());
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("any-view", events, null);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle empty events list")
    void shouldHandleEmptyEventsList() throws Exception {
        // Given
        TestProjector projector = new TestProjector("wallet-view");
        ViewEventHandler handler = new ViewEventHandler(List.of(projector));

        // When
        int result = handler.handle("wallet-view", List.of(), null);

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
        ViewEventHandler handler = new ViewEventHandler(List.of(projector));
        List<StoredEvent> events = createTestEvents();

        // When
        handler.handle("wallet-view", events, null);

        // Then
        assertThat(projector.lastEventsReceived).isSameAs(events);
    }

    @Test
    @DisplayName("Should return count from projector")
    void shouldReturnCount_FromProjector() throws Exception {
        // Given
        CountingProjector projector = new CountingProjector("wallet-view", 5);
        ViewEventHandler handler = new ViewEventHandler(List.of(projector));
        List<StoredEvent> events = createTestEvents();

        // When
        int result = handler.handle("wallet-view", events, null);

        // Then
        assertThat(result).isEqualTo(5);
    }

    // Test implementations

    static class TestProjector implements ViewProjector {
        private final String viewName;
        int handledCount = 0;
        List<StoredEvent> lastEventsReceived;

        TestProjector(String viewName) {
            this.viewName = viewName;
        }

        @Override
        public String getViewName() {
            return viewName;
        }

        @Override
        public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
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
        public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
            throw new RuntimeException("Test exception");
        }
    }

    static class DataSourceCapturingProjector implements ViewProjector {
        private final String viewName;
        DataSource receivedDataSource;

        DataSourceCapturingProjector(String viewName) {
            this.viewName = viewName;
        }

        @Override
        public String getViewName() {
            return viewName;
        }

        @Override
        public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
            receivedDataSource = writeDataSource;
            return events.size();
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
        public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) throws Exception {
            return returnCount;
        }
    }

    static class TestDataSource implements DataSource {
        // Minimal implementation for testing
        @Override
        public java.sql.Connection getConnection() {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public int getLoginTimeout() {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException("Not implemented for test");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            throw new UnsupportedOperationException("Not implemented for test");
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

