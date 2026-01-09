package com.crablet.command;

import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.store.Cursor;
import com.crablet.eventstore.store.SequenceNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CommandResult.
 * Tests critical methods used in CommandExecutorImpl.
 */
@DisplayName("CommandResult Unit Tests")
class CommandResultTest {

    @Test
    @DisplayName("isEmpty should return true when events list is empty")
    void isEmpty_WithEmptyEventsList_ShouldReturnTrue() {
        // Given
        CommandResult result = new CommandResult(List.of(), AppendCondition.expectEmptyStream(), null);

        // When
        boolean isEmpty = result.isEmpty();

        // Then
        assertThat(isEmpty).isTrue();
    }

    @Test
    @DisplayName("isEmpty should return false when events list has events")
    void isEmpty_WithNonEmptyEventsList_ShouldReturnFalse() {
        // Given
        AppendEvent event = AppendEvent.builder("TestEvent")
                .tag("key", "value")
                .data("{}")
                .build();
        CommandResult result = new CommandResult(List.of(event), AppendCondition.expectEmptyStream(), null);

        // When
        boolean isEmpty = result.isEmpty();

        // Then
        assertThat(isEmpty).isFalse();
    }

    @Test
    @DisplayName("of should create result with events and condition, setting reason to null")
    void of_WithEventsAndCondition_ShouldCreateResultWithNullReason() {
        // Given
        AppendEvent event = AppendEvent.builder("TestEvent")
                .tag("key", "value")
                .data("{}")
                .build();
        AppendCondition condition = AppendCondition.expectEmptyStream();

        // When
        CommandResult result = CommandResult.of(List.of(event), condition);

        // Then
        assertThat(result.events()).isEqualTo(List.of(event));
        assertThat(result.appendCondition()).isEqualTo(condition);
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("of should preserve events and condition")
    void of_WithEventsAndCondition_ShouldPreserveEventsAndCondition() {
        // Given
        AppendEvent event1 = AppendEvent.builder("Event1")
                .tag("key1", "value1")
                .data("{}")
                .build();
        AppendEvent event2 = AppendEvent.builder("Event2")
                .tag("key2", "value2")
                .data("{}")
                .build();
        Cursor cursor = Cursor.of(SequenceNumber.of(100L), Instant.now(), "tx-123");
        AppendCondition condition = AppendCondition.of(cursor);

        // When
        CommandResult result = CommandResult.of(List.of(event1, event2), condition);

        // Then
        assertThat(result.events()).hasSize(2);
        assertThat(result.events()).containsExactly(event1, event2);
        assertThat(result.appendCondition()).isEqualTo(condition);
    }

    @Test
    @DisplayName("empty should create result with empty events list")
    void empty_ShouldCreateEmptyResult() {
        // When
        CommandResult result = CommandResult.empty();

        // Then
        assertThat(result.events()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("empty should set reason to null")
    void empty_ShouldSetReasonToNull() {
        // When
        CommandResult result = CommandResult.empty();

        // Then
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("empty should use expectEmptyStream condition")
    void empty_ShouldUseExpectEmptyStreamCondition() {
        // When
        CommandResult result = CommandResult.empty();

        // Then
        assertThat(result.appendCondition()).isEqualTo(AppendCondition.expectEmptyStream());
        assertThat(result.appendCondition().afterCursor().position().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("isEmpty with null events should throw NPE when accessed")
    void isEmpty_WithNullEventsList_ShouldThrowNPE() {
        // Given - Records allow null in constructor, but throw NPE when accessing null fields
        CommandResult result = new CommandResult(null, AppendCondition.expectEmptyStream(), null);

        // When & Then - isEmpty() calls events.isEmpty() which throws NPE on null
        assertThatThrownBy(() ->
                result.isEmpty()
        ).isInstanceOf(NullPointerException.class);
    }
}

