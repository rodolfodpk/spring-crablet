package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StoredEvent value object.
 * Tests record constructor, hasTag/hasAnyTag methods, equality/hashCode, and edge cases.
 */
@DisplayName("StoredEvent Unit Tests")
class StoredEventTest {

    @Test
    @DisplayName("Should create StoredEvent with all parameters")
    void shouldCreateStoredEvent_WithAllParameters() {
        // Given
        String type = "WalletOpened";
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        byte[] data = "test-data".getBytes();
        String transactionId = "tx-123";
        long position = 100L;
        Instant occurredAt = Instant.now();

        // When
        StoredEvent event = new StoredEvent(type, tags, data, transactionId, position, occurredAt);

        // Then
        assertThat(event.type()).isEqualTo(type);
        assertThat(event.tags()).isEqualTo(tags);
        assertThat(event.data()).isEqualTo(data);
        assertThat(event.transactionId()).isEqualTo(transactionId);
        assertThat(event.position()).isEqualTo(position);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("Should check hasTag with matching tag")
    void shouldCheckHasTag_WithMatchingTag() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.hasTag("wallet_id", "wallet-123")).isTrue();
    }

    @Test
    @DisplayName("Should check hasTag with non-matching tag")
    void shouldCheckHasTag_WithNonMatchingTag() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.hasTag("wallet_id", "wallet-456")).isFalse();
        assertThat(event.hasTag("user_id", "wallet-123")).isFalse();
    }

    @Test
    @DisplayName("Should check hasAnyTag with matching tag")
    void shouldCheckHasAnyTag_WithMatchingTag() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(
                        new Tag("wallet_id", "wallet-123"),
                        new Tag("user_id", "user-456")
                ),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.hasAnyTag(List.of(new Tag("wallet_id", "wallet-123")))).isTrue();
        assertThat(event.hasAnyTag(List.of(new Tag("user_id", "user-456")))).isTrue();
    }

    @Test
    @DisplayName("Should check hasAnyTag with non-matching tag")
    void shouldCheckHasAnyTag_WithNonMatchingTag() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.hasAnyTag(List.of(new Tag("wallet_id", "wallet-456")))).isFalse();
        assertThat(event.hasAnyTag(List.of(new Tag("user_id", "user-456")))).isFalse();
    }

    @Test
    @DisplayName("Should check hasAnyTag with multiple tags (some matching)")
    void shouldCheckHasAnyTag_WithMultipleTags() {
        // Given
        StoredEvent event = new StoredEvent(
                "DepositMade",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then - Should return true if ANY tag matches
        assertThat(event.hasAnyTag(List.of(
                new Tag("wallet_id", "wallet-456"),  // Non-matching
                new Tag("wallet_id", "wallet-123")   // Matching
        ))).isTrue();
    }

    @Test
    @DisplayName("Should check hasAnyTag with multiple tags (none matching)")
    void shouldCheckHasAnyTag_WithMultipleTagsNoneMatching() {
        // Given
        StoredEvent event = new StoredEvent(
                "DepositMade",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.hasAnyTag(List.of(
                new Tag("wallet_id", "wallet-456"),
                new Tag("user_id", "user-456")
        ))).isFalse();
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        String type = "WalletOpened";
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        byte[] data = "test".getBytes();
        String transactionId = "tx-123";
        long position = 100L;
        Instant occurredAt = Instant.now();

        // When
        StoredEvent event1 = new StoredEvent(type, tags, data, transactionId, position, occurredAt);
        StoredEvent event2 = new StoredEvent(type, tags, data, transactionId, position, occurredAt);

        // Then
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different positions")
    void shouldImplementEquals_WithDifferentPositions() {
        // Given
        String type = "WalletOpened";
        List<Tag> tags = List.of(new Tag("wallet_id", "wallet-123"));
        byte[] data = "test".getBytes();
        String transactionId = "tx-123";
        Instant occurredAt = Instant.now();

        // When
        StoredEvent event1 = new StoredEvent(type, tags, data, transactionId, 100L, occurredAt);
        StoredEvent event2 = new StoredEvent(type, tags, data, transactionId, 200L, occurredAt);

        // Then
        assertThat(event1).isNotEqualTo(event2);
    }

    @Test
    @DisplayName("Should handle empty tags list")
    void shouldHandleEmptyTagsList() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then
        assertThat(event.tags()).isEmpty();
        assertThat(event.hasTag("wallet_id", "wallet-123")).isFalse();
        assertThat(event.hasAnyTag(List.of(new Tag("wallet_id", "wallet-123")))).isFalse();
    }

    @Test
    @DisplayName("Should handle empty data array")
    void shouldHandleEmptyDataArray() {
        // Given
        byte[] emptyData = new byte[0];

        // When
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-123")),
                emptyData,
                "tx-123",
                100L,
                Instant.now()
        );

        // Then
        assertThat(event.data()).isEmpty();
        assertThat(event.data().length).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle null tags")
    void shouldHandleNullTags_ShouldAllow() {
        // Given - Records allow null fields unless validated
        byte[] data = "test".getBytes();

        // When - Direct constructor allows null
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                null,
                data,
                "tx-123",
                100L,
                Instant.now()
        );

        // Then
        assertThat(event.tags()).isNull();
        // Note: hasTag/hasAnyTag will throw NPE if tags is null, but that's expected behavior
    }

    @Test
    @DisplayName("Should handle hasAnyTag with empty list")
    void shouldHandleHasAnyTag_WithEmptyList() {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new Tag("wallet_id", "wallet-123")),
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then - Empty list means no tags to match
        assertThat(event.hasAnyTag(List.of())).isFalse();
    }

    @Test
    @DisplayName("Should handle hasAnyTag with null tags in event")
    void shouldHandleHasAnyTag_WithNullTagsInEvent() {
        // Given - Event with null tags
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                null,
                "test".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );

        // When & Then - Should throw NPE when accessing null tags
        assertThatThrownBy(() -> event.hasAnyTag(List.of(new Tag("wallet_id", "wallet-123"))))
                .isInstanceOf(NullPointerException.class);
    }
}

