package crablet.unit.core;

import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StateProjector interface to ensure proper event handling logic.
 * This tests the default handles() method implementation which is critical for
 * event filtering and state projection in the DCB pattern.
 */
@DisplayName("StateProjector Interface Tests")
class StateProjectorTest {

    @Test
    @DisplayName("Should handle event when event types match")
    void shouldHandleEventWhenEventTypesMatch() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened", "MoneyTransferred"),
                List.of()
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should not handle event when event types do not match")
    void shouldNotHandleEventWhenEventTypesDoNotMatch() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened", "MoneyTransferred"),
                List.of()
        );
        StoredEvent event = new StoredEvent("DepositMade", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    @Test
    @DisplayName("Should handle event when event types list is empty")
    void shouldHandleEventWhenEventTypesListIsEmpty() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of()
        );
        StoredEvent event = new StoredEvent("AnyEvent", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should handle event when tags match")
    void shouldHandleEventWhenTagsMatch() {
        // Given
        Tag tag1 = new Tag("wallet", "wallet-123");
        Tag tag2 = new Tag("account", "account-456");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of(tag1, tag2)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(tag1), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should not handle event when tags do not match")
    void shouldNotHandleEventWhenTagsDoNotMatch() {
        // Given
        Tag projectorTag = new Tag("wallet", "wallet-123");
        Tag eventTag = new Tag("account", "account-456");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of(projectorTag)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(eventTag), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    @Test
    @DisplayName("Should handle event when tags list is empty")
    void shouldHandleEventWhenTagsListIsEmpty() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of()
        );
        StoredEvent event = new StoredEvent("AnyEvent", List.of(new Tag("any", "value")), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should handle event when both event types and tags match")
    void shouldHandleEventWhenBothEventTypesAndTagsMatch() {
        // Given
        Tag tag = new Tag("wallet", "wallet-123");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened"),
                List.of(tag)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(tag), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should not handle event when event types match but tags do not")
    void shouldNotHandleEventWhenEventTypesMatchButTagsDoNot() {
        // Given
        Tag projectorTag = new Tag("wallet", "wallet-123");
        Tag eventTag = new Tag("account", "account-456");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened"),
                List.of(projectorTag)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(eventTag), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    @Test
    @DisplayName("Should not handle event when tags match but event types do not")
    void shouldNotHandleEventWhenTagsMatchButEventTypesDoNot() {
        // Given
        Tag tag = new Tag("wallet", "wallet-123");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened"),
                List.of(tag)
        );
        StoredEvent event = new StoredEvent("DepositMade", List.of(tag), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    @Test
    @DisplayName("Should handle event with multiple tags when any tag matches")
    void shouldHandleEventWithMultipleTagsWhenAnyTagMatches() {
        // Given
        Tag projectorTag1 = new Tag("wallet", "wallet-123");
        Tag projectorTag2 = new Tag("account", "account-456");
        Tag eventTag1 = new Tag("wallet", "wallet-123");
        Tag eventTag2 = new Tag("other", "other-value");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of(projectorTag1, projectorTag2)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(eventTag1, eventTag2), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should handle event with no tags when projector has tags")
    void shouldHandleEventWithNoTagsWhenProjectorHasTags() {
        // Given
        Tag projectorTag = new Tag("wallet", "wallet-123");
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of(projectorTag)
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    @Test
    @DisplayName("Should handle event with tags when projector has no tags")
    void shouldHandleEventWithTagsWhenProjectorHasNoTags() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of()
        );
        StoredEvent event = new StoredEvent("WalletOpened", List.of(new Tag("wallet", "wallet-123")), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should handle event when both event types and tags lists are empty")
    void shouldHandleEventWhenBothEventTypesAndTagsListsAreEmpty() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of(),
                List.of()
        );
        StoredEvent event = new StoredEvent("AnyEvent", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should handle event with multiple event types when event matches one")
    void shouldHandleEventWithMultipleEventTypesWhenEventMatchesOne() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened", "MoneyTransferred", "DepositMade"),
                List.of()
        );
        StoredEvent event = new StoredEvent("MoneyTransferred", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isTrue();
    }

    @Test
    @DisplayName("Should not handle event with multiple event types when event matches none")
    void shouldNotHandleEventWithMultipleEventTypesWhenEventMatchesNone() {
        // Given
        StateProjector<Map<String, Object>> projector = new TestStateProjector(
                "test-projector",
                List.of("WalletOpened", "MoneyTransferred", "DepositMade"),
                List.of()
        );
        StoredEvent event = new StoredEvent("WithdrawalMade", List.of(), "{}".getBytes(), "1", 1L, java.time.Instant.now());

        // When
        boolean handles = projector.handles(event);

        // Then
        assertThat(handles).isFalse();
    }

    // Test implementation of StateProjector
    private static class TestStateProjector implements StateProjector<Map<String, Object>> {
        private final String id;
        private final List<String> eventTypes;
        private final List<Tag> tags;

        public TestStateProjector(String id, List<String> eventTypes, List<Tag> tags) {
            this.id = id;
            this.eventTypes = eventTypes;
            this.tags = tags;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<String> getEventTypes() {
            return eventTypes;
        }

        @Override
        public List<Tag> getTags() {
            return tags;
        }

        @Override
        public Map<String, Object> getInitialState() {
            return Map.of("initialized", true);
        }

        @Override
        public Map<String, Object> transition(Map<String, Object> currentState, StoredEvent event) {
            return currentState;
        }
    }
}
