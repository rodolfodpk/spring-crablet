package com.crablet.eventstore.query;

import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProjectionResult value object.
 * Tests factory methods, equality/hashCode, and null handling.
 */
@DisplayName("ProjectionResult Unit Tests")
class ProjectionResultTest {

    @Test
    @DisplayName("Should create ProjectionResult with states and cursor")
    void shouldCreateProjectionResult_WithStatesAndCursor() {
        // Given
        String state = "test-state";
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, cursor);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.cursor()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("Should create ProjectionResult with states only")
    void shouldCreateProjectionResult_WithStatesOnly() {
        // Given
        String state = "test-state";

        // When
        ProjectionResult<String> result = ProjectionResult.of(state);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.cursor()).isNull();
    }

    @Test
    @DisplayName("Should return state using state method")
    void shouldReturnState_UsingStateMethod() {
        // Given
        String state = "test-state";
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, cursor);

        // Then
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.state()).isSameAs(result.states());
    }

    @Test
    @DisplayName("Should return state using states accessor")
    void shouldReturnState_UsingStatesAccessor() {
        // Given
        String state = "test-state";
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, cursor);

        // Then
        assertThat(result.states()).isEqualTo(state);
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        String state = "test-state";
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of(state, cursor);
        ProjectionResult<String> result2 = ProjectionResult.of(state, cursor);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different states")
    void shouldImplementEquals_WithDifferentStates() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of("state1", cursor);
        ProjectionResult<String> result2 = ProjectionResult.of("state2", cursor);

        // Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("Should implement equals with different cursors")
    void shouldImplementEquals_WithDifferentCursors() {
        // Given
        String state = "test-state";
        Cursor cursor1 = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        Cursor cursor2 = Cursor.of(SequenceNumber.of(200L), Instant.now(), "tx-456");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of(state, cursor1);
        ProjectionResult<String> result2 = ProjectionResult.of(state, cursor2);

        // Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("Should handle null state")
    void shouldHandleNullState_ShouldAllow() {
        // Given
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of((String) null, cursor);

        // Then
        assertThat(result.states()).isNull();
        assertThat(result.cursor()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("Should handle null cursor")
    void shouldHandleNullCursor_ShouldAllow() {
        // Given
        String state = "test-state";

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, (Cursor) null);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.cursor()).isNull();
    }

    @Test
    @DisplayName("Should handle null state and null cursor")
    void shouldHandleNullStateAndNullCursor() {
        // When
        ProjectionResult<String> result = ProjectionResult.of((String) null, (Cursor) null);

        // Then
        assertThat(result.states()).isNull();
        assertThat(result.cursor()).isNull();
    }

    @Test
    @DisplayName("Should handle complex state types")
    void shouldHandleComplexStateTypes() {
        // Given
        TestState state = new TestState("id", 100);
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<TestState> result = ProjectionResult.of(state, cursor);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.cursor()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        String state = "test-state";
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, cursor);

        // Then - Record auto-generates toString()
        String toString = result.toString();
        assertThat(toString)
                .contains("ProjectionResult[")
                .contains("states=")
                .contains("cursor=");
    }

    // Helper class for testing complex types
    private record TestState(String id, int value) {}
}

