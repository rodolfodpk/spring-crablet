package com.crablet.eventstore.query;

import com.crablet.eventstore.store.StreamPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProjectionResult value object.
 * Tests factory methods, equality/hashCode, and null handling.
 */
@DisplayName("ProjectionResult Unit Tests")
class ProjectionResultTest {

    @Test
    @DisplayName("Should create ProjectionResult with states and stream position")
    void shouldCreateProjectionResult_WithStatesAndCursor() {
        // Given
        String state = "test-state";
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, streamPosition);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.streamPosition()).isEqualTo(streamPosition);
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
        assertThat(result.streamPosition()).isNull();
    }

    @Test
    @DisplayName("Should return state using state method")
    void shouldReturnState_UsingStateMethod() {
        // Given
        String state = "test-state";
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, streamPosition);

        // Then
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.state()).isSameAs(result.states());
    }

    @Test
    @DisplayName("Should return state using states accessor")
    void shouldReturnState_UsingStatesAccessor() {
        // Given
        String state = "test-state";
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, streamPosition);

        // Then
        assertThat(result.states()).isEqualTo(state);
    }

    @Test
    @DisplayName("Should implement equals with same values")
    void shouldImplementEquals_WithSameValues() {
        // Given
        String state = "test-state";
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of(state, streamPosition);
        ProjectionResult<String> result2 = ProjectionResult.of(state, streamPosition);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    @DisplayName("Should implement equals with different states")
    void shouldImplementEquals_WithDifferentStates() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of("state1", streamPosition);
        ProjectionResult<String> result2 = ProjectionResult.of("state2", streamPosition);

        // Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("Should implement equals with different cursors")
    void shouldImplementEquals_WithDifferentCursors() {
        // Given
        String state = "test-state";
        StreamPosition cursor1 = StreamPosition.of(100L, Instant.now(), "tx-123");
        StreamPosition cursor2 = StreamPosition.of(200L, Instant.now(), "tx-456");

        // When
        ProjectionResult<String> result1 = ProjectionResult.of(state, cursor1);
        ProjectionResult<String> result2 = ProjectionResult.of(state, cursor2);

        // Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    @DisplayName("Should handle null state")
    @SuppressWarnings("NullAway")
    void shouldHandleNullState_ShouldAllow() {
        // Given
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of((String) null, streamPosition);

        // Then
        assertThat(result.states()).isNull();
        assertThat(result.streamPosition()).isEqualTo(streamPosition);
    }

    @Test
    @DisplayName("Should handle null stream position")
    void shouldHandleNullCursor_ShouldAllow() {
        // Given
        String state = "test-state";

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, (StreamPosition) null);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.streamPosition()).isNull();
    }

    @Test
    @DisplayName("Should handle null state and null stream position")
    @SuppressWarnings("NullAway")
    void shouldHandleNullStateAndNullCursor() {
        // When
        ProjectionResult<String> result = ProjectionResult.of((String) null, (StreamPosition) null);

        // Then
        assertThat(result.states()).isNull();
        assertThat(result.streamPosition()).isNull();
    }

    @Test
    @DisplayName("Should handle complex state types")
    void shouldHandleComplexStateTypes() {
        // Given
        TestState state = new TestState("id", 100);
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<TestState> result = ProjectionResult.of(state, streamPosition);

        // Then
        assertThat(result.states()).isEqualTo(state);
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.streamPosition()).isEqualTo(streamPosition);
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString_Correctly() {
        // Given
        String state = "test-state";
        StreamPosition streamPosition = StreamPosition.of(100L, Instant.now(), "tx-123");

        // When
        ProjectionResult<String> result = ProjectionResult.of(state, streamPosition);

        // Then - Record auto-generates toString()
        String toString = result.toString();
        assertThat(toString)
                .contains("ProjectionResult[")
                .contains("states=")
                .contains("streamPosition=");
    }

    // Helper class for testing complex types
    private record TestState(String id, int value) {}
}

