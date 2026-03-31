package com.crablet.eventstore.dcb;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
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
        Cursor afterCursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");
        Query failIfEventsMatch = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterCursor, failIfEventsMatch);

        // Then
        assertThat(condition.afterCursor()).isEqualTo(afterCursor);
        assertThat(condition.stateChanged()).isEqualTo(failIfEventsMatch);
    }

    @Test
    @DisplayName("Should create AppendCondition with default failIfEventsMatch")
    void shouldCreateAppendConditionWithDefaultFailIfEventsMatch() {
        // Given
        Cursor afterCursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");

        // When
        AppendCondition condition = AppendCondition.of(afterCursor);

        // Then
        assertThat(condition.afterCursor()).isEqualTo(afterCursor);
        assertThat(condition.stateChanged()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("Should create AppendCondition with empty() for commutative operations and new streams")
    void shouldCreateAppendConditionWithEmptyMethod() {
        // When
        AppendCondition condition = AppendCondition.empty();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(Cursor.zero());
        assertThat(condition.stateChanged()).isEqualTo(Query.empty());
        assertThat(condition.alreadyExists()).isNull();
        assertThat(condition.afterCursor().position().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw exception when afterCursor is null")
    void shouldThrowExceptionWhenAfterCursorIsNull() {
        // Given
        Query failIfEventsMatch = Query.empty();

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(null, failIfEventsMatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("afterCursor cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when failIfEventsMatch is null")
    void shouldThrowExceptionWhenFailIfEventsMatchIsNull() {
        // Given
        Cursor afterCursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(afterCursor, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stateChanged cannot be null");
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        Instant fixedTime = Instant.now();
        Cursor afterCursor1 = Cursor.of(SequenceNumber.of(100L), fixedTime, "12345");
        Cursor afterCursor2 = Cursor.of(SequenceNumber.of(100L), fixedTime, "12345");
        Query failIfEventsMatch = Query.empty();

        // When
        AppendCondition condition1 = AppendCondition.of(afterCursor1, failIfEventsMatch);
        AppendCondition condition2 = AppendCondition.of(afterCursor2, failIfEventsMatch);

        // Then
        assertThat(condition1).isEqualTo(condition2);
        assertThat(condition1.hashCode()).isEqualTo(condition2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals correctly with different cursors")
    void shouldImplementEqualsCorrectlyWithDifferentCursors() {
        // Given
        Cursor afterCursor1 = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");
        Cursor afterCursor2 = Cursor.of(SequenceNumber.of(200L), Instant.now(), "12345");
        Query failIfEventsMatch = Query.empty();

        // When
        AppendCondition condition1 = AppendCondition.of(afterCursor1, failIfEventsMatch);
        AppendCondition condition2 = AppendCondition.of(afterCursor2, failIfEventsMatch);

        // Then
        assertThat(condition1).isNotEqualTo(condition2);
    }

    @Test
    @DisplayName("Should implement equals correctly with different queries")
    void shouldImplementEqualsCorrectlyWithDifferentQueries() {
        // Given
        Cursor afterCursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");
        Query failIfEventsMatch1 = Query.empty();
        Query failIfEventsMatch2 = Query.of(QueryItem.of(List.of("WalletOpened"), List.of()));

        // When
        AppendCondition condition1 = AppendCondition.of(afterCursor, failIfEventsMatch1);
        AppendCondition condition2 = AppendCondition.of(afterCursor, failIfEventsMatch2);

        // Then
        assertThat(condition1).isNotEqualTo(condition2);
    }

    @Test
    @DisplayName("Should have correct string representation")
    void shouldHaveCorrectStringRepresentation() {
        // Given
        Cursor afterCursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "12345");
        Query failIfEventsMatch = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(afterCursor, failIfEventsMatch);

        // Then - Record auto-generates toString() in format "RecordName[field1=value1, field2=value2]"
        assertThat(condition.toString())
                .contains("AppendCondition[")
                .contains("afterCursor=")
                .contains("stateChanged=");
    }

    @Test
    @DisplayName("Should detect empty stream correctly")
    void shouldDetectEmptyStreamCorrectly() {
        // Given
        Cursor zeroCursor = Cursor.zero();
        Cursor nonZeroCursor = Cursor.of(SequenceNumber.of(1L), Instant.now(), "12345");

        // When
        AppendCondition emptyStreamCondition = AppendCondition.of(zeroCursor);
        AppendCondition nonEmptyStreamCondition = AppendCondition.of(nonZeroCursor);

        // Then
        assertThat(emptyStreamCondition.afterCursor().position().value()).isEqualTo(0);
        assertThat(nonEmptyStreamCondition.afterCursor().position().value()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Should create AppendCondition with idempotency check")
    void shouldCreateAppendConditionWithIdempotencyCheck() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query stateChangedQuery = Query.empty();
        Query alreadyExistsQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(cursor, stateChangedQuery, alreadyExistsQuery);

        // Then
        assertThat(condition.afterCursor()).isEqualTo(cursor);
        assertThat(condition.stateChanged()).isEqualTo(stateChangedQuery);
        assertThat(condition.alreadyExists()).isEqualTo(alreadyExistsQuery);
    }

    @Test
    @DisplayName("Should throw exception when stateChangedQuery is null")
    void shouldThrowExceptionWhenStateChangedQueryIsNull() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When & Then
        assertThatThrownBy(() -> AppendCondition.of(cursor, null, Query.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stateChangedQuery cannot be null");
    }

    @Test
    @DisplayName("Should allow null alreadyExistsQuery")
    void shouldAllowNullAlreadyExistsQuery() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Query stateChangedQuery = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(cursor, stateChangedQuery, null);

        // Then
        assertThat(condition.alreadyExists()).isNull();
    }

    @Test
    @DisplayName("Should create AppendCondition with cursor at zero")
    void shouldCreateAppendConditionWithCursorAtZero() {
        // Given
        Cursor zeroCursor = Cursor.zero();
        Query query = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(zeroCursor, query);

        // Then
        assertThat(condition.afterCursor().position().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should create AppendCondition with cursor at max value")
    void shouldCreateAppendConditionWithCursorAtMaxValue() {
        // Given
        Cursor maxCursor = Cursor.of(SequenceNumber.of(Long.MAX_VALUE), Instant.now(), "tx-123");
        Query query = Query.empty();

        // When
        AppendCondition condition = AppendCondition.of(maxCursor, query);

        // Then
        assertThat(condition.afterCursor().position().value()).isEqualTo(Long.MAX_VALUE);
    }

    // ===== AppendCondition.idempotent() =====

    @Test
    @DisplayName("idempotent() should start from Cursor.zero (no prior events required)")
    void idempotentStartsFromCursorZero() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.afterCursor()).isEqualTo(Cursor.zero());
    }

    @Test
    @DisplayName("idempotent() stateChanged should be empty (no cursor-based check)")
    void idempotentStateChangedIsEmpty() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.stateChanged()).isEqualTo(Query.empty());
    }

    @Test
    @DisplayName("idempotent() alreadyExists should be non-null (idempotency check present)")
    void idempotentAlreadyExistsIsNonNull() {
        AppendCondition condition = AppendCondition.idempotent("WalletOpened", "wallet_id", "w1");

        assertThat(condition.alreadyExists()).isNotNull();
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