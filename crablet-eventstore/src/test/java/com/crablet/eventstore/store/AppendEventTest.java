package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AppendEvent value object and Builder.
 * Tests record constructor, builder pattern, validation, and edge cases.
 */
@DisplayName("AppendEvent Unit Tests")
class AppendEventTest {

    @Test
    @DisplayName("Should create AppendEvent with record constructor")
    void shouldCreateAppendEvent_WithRecordConstructor() {
        // Given
        String type = "WalletOpened";
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = new AppendEvent(type, tags, eventData);

        // Then
        assertThat(event.type()).isEqualTo(type);
        assertThat(event.tags()).isEqualTo(tags);
        assertThat(event.eventData()).isEqualTo(eventData);
    }

    @Test
    @DisplayName("Should create AppendEvent with builder")
    void shouldCreateAppendEvent_WithBuilder() {
        // Given
        String type = "WalletOpened";
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = AppendEvent.builder(type)
                .tag("wallet_id", "wallet-123")
                .data(eventData)
                .build();

        // Then
        assertThat(event.type()).isEqualTo(type);
        assertThat(event.tags()).hasSize(1);
        assertThat(event.tags().get(0)).isEqualTo(new Tag("wallet_id", "wallet-123"));
        assertThat(event.eventData()).isEqualTo(eventData);
    }

    @Test
    @DisplayName("Should add multiple tags with builder")
    void shouldAddMultipleTags_WithBuilder() {
        // Given
        String type = "DepositMade";
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = AppendEvent.builder(type)
                .tag("wallet_id", "wallet-123")
                .tag("deposit_id", "deposit-456")
                .tag("user_id", "user-789")
                .data(eventData)
                .build();

        // Then
        assertThat(event.tags()).hasSize(3);
        assertThat(event.tags()).containsExactly(
                new Tag("wallet_id", "wallet-123"),
                new Tag("deposit_id", "deposit-456"),
                new Tag("user_id", "user-789")
        );
    }

    @Test
    @DisplayName("Should throw exception when event data is null")
    void shouldThrowException_WhenEventDataIsNull() {
        // Given
        String type = "WalletOpened";

        // When & Then
        assertThatThrownBy(() -> AppendEvent.builder(type)
                .tag("wallet_id", "wallet-123")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event data cannot be null");
    }

    @Test
    @DisplayName("Should check hasTag with matching tag")
    void shouldCheckHasTag_WithMatchingTag() {
        // Given
        AppendEvent event = AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-123")
                .data(new TestEventData("test", 0))
                .build();

        // When & Then
        assertThat(event.hasTag("wallet_id", "wallet-123")).isTrue();
    }

    @Test
    @DisplayName("Should check hasTag with non-matching tag")
    void shouldCheckHasTag_WithNonMatchingTag() {
        // Given
        AppendEvent event = AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-123")
                .data(new TestEventData("test", 0))
                .build();

        // When & Then
        assertThat(event.hasTag("wallet_id", "wallet-456")).isFalse();
        assertThat(event.hasTag("user_id", "wallet-123")).isFalse();
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        String type = "WalletOpened";
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event1 = new AppendEvent(type, tags, eventData);
        AppendEvent event2 = new AppendEvent(type, tags, eventData);

        // Then
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different types")
    void shouldImplementEquals_WithDifferentTypes() {
        // Given
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event1 = new AppendEvent("WalletOpened", tags, eventData);
        AppendEvent event2 = new AppendEvent("DepositMade", tags, eventData);

        // Then
        assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("Should handle empty tags list")
    void shouldHandleEmptyTagsList() {
        // Given
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = AppendEvent.builder("WalletOpened")
                .data(eventData)
                .build();

        // Then
        assertThat(event.tags()).isEmpty();
        assertThat(event.hasTag("wallet_id", "wallet-123")).isFalse();
    }

    @Test
    @DisplayName("Should handle multiple tags")
    void shouldHandleMultipleTags() {
        // Given
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = AppendEvent.builder("DepositMade")
                .tag("wallet_id", "wallet-123")
                .tag("deposit_id", "deposit-456")
                .tag("transaction_id", "tx-789")
                .data(eventData)
                .build();

        // Then
        assertThat(event.tags()).hasSize(3);
        assertThat(event.hasTag("wallet_id", "wallet-123")).isTrue();
        assertThat(event.hasTag("deposit_id", "deposit-456")).isTrue();
        assertThat(event.hasTag("transaction_id", "tx-789")).isTrue();
    }

    @Test
    @DisplayName("Should create immutable tags list")
    void shouldCreateImmutableTagsList() {
        // Given
        Object eventData = new TestEventData("test-id", 100);

        // When
        AppendEvent event = AppendEvent.builder("WalletOpened")
                .tag("wallet_id", "wallet-123")
                .data(eventData)
                .build();

        // Then - Tags list should be immutable (List.copyOf() in builder)
        assertThatThrownBy(() -> event.tags().add(new Tag("new", "tag")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // Helper class for testing event data
    private record TestEventData(String id, int value) {}
}

