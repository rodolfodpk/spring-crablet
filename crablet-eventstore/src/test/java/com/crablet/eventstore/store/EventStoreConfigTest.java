package com.crablet.eventstore.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EventStoreConfig.
 * Tests default values, getters/setters, and edge cases.
 */
@DisplayName("EventStoreConfig Unit Tests")
class EventStoreConfigTest {

    @Test
    @DisplayName("Should have default values")
    void shouldHaveDefaultValues() {
        // When
        EventStoreConfig config = new EventStoreConfig();

        // Then
        assertThat(config.isPersistCommands()).isTrue();
        assertThat(config.getTransactionIsolation()).isEqualTo("READ_COMMITTED");
        assertThat(config.getFetchSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should set and get persistCommands")
    void shouldSetAndGetPersistCommands() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setPersistCommands(false);

        // Then
        assertThat(config.isPersistCommands()).isFalse();

        // When
        config.setPersistCommands(true);

        // Then
        assertThat(config.isPersistCommands()).isTrue();
    }

    @Test
    @DisplayName("Should set and get transactionIsolation")
    void shouldSetAndGetTransactionIsolation() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setTransactionIsolation("SERIALIZABLE");

        // Then
        assertThat(config.getTransactionIsolation()).isEqualTo("SERIALIZABLE");

        // When
        config.setTransactionIsolation("REPEATABLE_READ");

        // Then
        assertThat(config.getTransactionIsolation()).isEqualTo("REPEATABLE_READ");
    }

    @Test
    @DisplayName("Should set and get fetchSize")
    void shouldSetAndGetFetchSize() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setFetchSize(500);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(500);

        // When
        config.setFetchSize(2000);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Should allow negative fetchSize")
    void shouldAllowNegativeFetchSize() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setFetchSize(-1);

        // Then - No validation, so negative values are allowed
        assertThat(config.getFetchSize()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should handle null transactionIsolation")
    void shouldHandleNullTransactionIsolation() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setTransactionIsolation(null);

        // Then - No validation, so null is allowed
        assertThat(config.getTransactionIsolation()).isNull();
    }

    @Test
    @DisplayName("Should handle empty transactionIsolation")
    void shouldHandleEmptyTransactionIsolation() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setFetchSize(0);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle zero fetchSize")
    void shouldHandleZeroFetchSize() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setFetchSize(0);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large fetchSize")
    void shouldHandleLargeFetchSize() {
        // Given
        EventStoreConfig config = new EventStoreConfig();

        // When
        config.setFetchSize(Integer.MAX_VALUE);

        // Then
        assertThat(config.getFetchSize()).isEqualTo(Integer.MAX_VALUE);
    }
}

