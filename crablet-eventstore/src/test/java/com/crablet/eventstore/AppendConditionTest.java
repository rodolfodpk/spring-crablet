package com.crablet.eventstore;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.StreamPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AppendCondition to ensure proper validation and factory methods.
 * This is critical for DCB concurrency control and event sourcing consistency.
 */
@DisplayName("AppendCondition Unit Tests")
class AppendConditionTest {

    @Test
    @DisplayName("Should create AppendCondition with valid parameters")
    void shouldCreateAppendConditionWithValidParameters() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "12345");
        Query concurrencyQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterPosition, concurrencyQuery);

        // Then
        assertThat(condition.afterPosition()).isEqualTo(afterPosition);
        assertThat(condition.concurrencyQuery()).isEqualTo(concurrencyQuery);
    }

    @Test
    @DisplayName("Should create AppendCondition with default concurrencyQuery")
    void shouldCreateAppendConditionWithDefaultConcurrencyQuery() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "12345");

        // When
        AppendCondition condition = AppendCondition.of(afterPosition);

        // Then
        assertThat(condition.afterPosition()).isEqualTo(afterPosition);
        assertThat(condition.concurrencyQuery()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("Should create AppendCondition with empty() for commutative operations")
    void shouldCreateAppendConditionWithEmptyMethod() {
        // When
        AppendCondition condition = AppendCondition.empty();

        // Then
        assertThat(condition.afterPosition()).isEqualTo(StreamPosition.zero());
        assertThat(condition.concurrencyQuery()).isEqualTo(Query.empty());
        assertThat(condition.idempotencyQuery()).isEqualTo(Query.empty());
        assertThat(condition.afterPosition().position()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw exception when afterPosition is null")
    void shouldThrowExceptionWhenAfterPositionIsNull() {
        // Given
        Query concurrencyQuery = Query.empty();

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(null, concurrencyQuery))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("afterPosition cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when concurrencyQuery is null")
    void shouldThrowExceptionWhenConcurrencyQueryIsNull() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "12345");

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(afterPosition, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("concurrencyQuery cannot be null");
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        Instant fixedTime = Instant.now();
        StreamPosition pos1 = StreamPosition.of(100L, fixedTime, "12345");
        StreamPosition pos2 = StreamPosition.of(100L, fixedTime, "12345");
        Query concurrencyQuery = Query.empty();

        // When
        AppendCondition condition1 = AppendCondition.of(pos1, concurrencyQuery);
        AppendCondition condition2 = AppendCondition.of(pos2, concurrencyQuery);

        // Then
        assertThat(condition1).isEqualTo(condition2);
        assertThat(condition1.hashCode()).isEqualTo(condition2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals correctly with different positions")
    void shouldImplementEqualsCorrectlyWithDifferentPositions() {
        // Given
        StreamPosition pos1 = StreamPosition.of(100L, Instant.now(), "12345");
        StreamPosition pos2 = StreamPosition.of(200L, Instant.now(), "12345");
        Query concurrencyQuery = Query.empty();

        // When
        AppendCondition condition1 = AppendCondition.of(pos1, concurrencyQuery);
        AppendCondition condition2 = AppendCondition.of(pos2, concurrencyQuery);

        // Then
        assertThat(condition1).isNotEqualTo(condition2);
    }

    @Test
    @DisplayName("Should implement equals correctly with different queries")
    void shouldImplementEqualsCorrectlyWithDifferentQueries() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "12345");
        Query query1 = Query.empty();
        Query query2 = Query.of(QueryItem.of(List.of("WalletOpened"), List.of()));

        // When
        AppendCondition condition1 = AppendCondition.of(afterPosition, query1);
        AppendCondition condition2 = AppendCondition.of(afterPosition, query2);

        // Then
        assertThat(condition1).isNotEqualTo(condition2);
    }

    @Test
    @DisplayName("Should have correct string representation")
    void shouldHaveCorrectStringRepresentation() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "12345");
        Query concurrencyQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterPosition, concurrencyQuery);

        // Then - Record auto-generates toString() in format "RecordName[field1=value1, field2=value2]"
        assertThat(condition.toString())
                .contains("AppendCondition[")
                .contains("afterPosition=")
                .contains("concurrencyQuery=");
    }

    @Test
    @DisplayName("Should detect empty stream correctly")
    void shouldDetectEmptyStreamCorrectly() {
        // Given
        StreamPosition zeroPosition = StreamPosition.zero();
        StreamPosition nonZeroPosition = StreamPosition.of(1L, Instant.now(), "12345");

        // When
        AppendCondition emptyStreamCondition = AppendCondition.of(zeroPosition);
        AppendCondition nonEmptyStreamCondition = AppendCondition.of(nonZeroPosition);

        // Then
        assertThat(emptyStreamCondition.afterPosition().position()).isEqualTo(0);
        assertThat(nonEmptyStreamCondition.afterPosition().position()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Should create AppendCondition with idempotency check")
    void shouldCreateAppendConditionWithIdempotencyCheck() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query concurrencyQuery = Query.empty();
        Query idempotencyQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterPosition, concurrencyQuery, idempotencyQuery);

        // Then
        assertThat(condition.afterPosition()).isEqualTo(afterPosition);
        assertThat(condition.concurrencyQuery()).isEqualTo(concurrencyQuery);
        assertThat(condition.idempotencyQuery()).isEqualTo(idempotencyQuery);
    }

    @Test
    @DisplayName("Should throw exception when concurrencyQuery arg is null")
    void shouldThrowExceptionWhenConcurrencyQueryArgIsNull() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(afterPosition, null, Query.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("concurrencyQuery cannot be null");
    }

    @Test
    @DisplayName("Should treat null idempotencyQuery as Query.empty()")
    void shouldTreatNullIdempotencyQueryAsEmpty() {
        // Given
        StreamPosition afterPosition = StreamPosition.of(100L, Instant.now(), "tx-123");
        Query concurrencyQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterPosition, concurrencyQuery, null);

        // Then — null is normalised to Query.empty(), not stored as null
        assertThat(condition.idempotencyQuery()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("Should create AppendCondition with afterPosition at zero")
    void shouldCreateAppendConditionWithPositionAtZero() {
        // Given
        StreamPosition zeroPosition = StreamPosition.zero();
        Query query = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(zeroPosition, query);

        // Then
        assertThat(condition.afterPosition().position()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create AppendCondition with afterPosition at max value")
    void shouldCreateAppendConditionWithPositionAtMaxValue() {
        // Given
        StreamPosition maxPosition = StreamPosition.of(Long.MAX_VALUE, Instant.now(), "tx-123");
        Query query = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(maxPosition, query);

        // Then
        assertThat(condition.afterPosition().position()).isEqualTo(Long.MAX_VALUE);
    }

    // ===== AppendCondition.idempotent() =====

    @Test
    @DisplayName("idempotent() should start from StreamPosition.zero (no prior events required)")
    void idempotentStartsFromPositionZero() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.afterPosition()).isEqualTo(StreamPosition.zero());
    }

    @Test
    @DisplayName("idempotent() concurrencyQuery should be empty (no position-based check)")
    void idempotentConcurrencyQueryIsEmpty() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.concurrencyQuery()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("idempotent() idempotencyQuery should be non-empty (idempotency check present)")
    void idempotentIdempotencyQueryIsNonEmpty() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.idempotencyQuery().items()).isNotEmpty();
    }

    @Test
    @DisplayName("idempotent() two calls with same args should produce equivalent conditions")
    void idempotentIsReproducible() {
        AppendCondition c1 = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");
        AppendCondition c2 = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(c1).isEqualTo(c2);
    }

    @Test
    @DisplayName("idempotent() conditions for different tag values should differ")
    void idempotentDiffersPerTagValue() {
        AppendCondition c1 = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");
        AppendCondition c2 = AppendCondition.idempotent("WalletOpened", "wallet_id", "w2");

        assertThat(c1).isNotEqualTo(c2);
    }
}
