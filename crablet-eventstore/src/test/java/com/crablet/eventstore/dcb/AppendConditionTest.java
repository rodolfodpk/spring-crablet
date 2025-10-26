package com.crablet.eventstore.dcb;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryItem;
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
    @DisplayName("Should create AppendCondition for empty stream")
    void shouldCreateAppendConditionForEmptyStream() {
        // When
        AppendCondition condition = AppendCondition.expectEmptyStream();

        // Then
        assertThat(condition.afterCursor()).isEqualTo(Cursor.zero());
        assertThat(condition.stateChanged()).isEqualTo(Query.empty());
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
}