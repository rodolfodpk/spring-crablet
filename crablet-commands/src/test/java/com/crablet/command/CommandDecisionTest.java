package com.crablet.command;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.query.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the sealed CommandDecision interface.
 * Tests each variant and the default events() convenience method.
 */
@DisplayName("CommandDecision Unit Tests")
class CommandDecisionTest {

    private static AppendEvent sampleEvent(String type) {
        return AppendEvent.builder(type)
                .tag("key", "value")
                .data("{}")
                .build();
    }

    // --- Commutative ---

    @Test
    @DisplayName("Commutative should carry events")
    void commutative_ShouldCarryEvents() {
        AppendEvent event = sampleEvent("TestEvent");
        CommandDecision result = new CommandDecision.Commutative(List.of(event));

        assertThat(result.events()).containsExactly(event);
        assertThat(result).isInstanceOf(CommandDecision.Commutative.class);
    }

    @Test
    @DisplayName("Commutative with multiple events should preserve order")
    void commutative_MultipleEvents_ShouldPreserveOrder() {
        AppendEvent e1 = sampleEvent("Event1");
        AppendEvent e2 = sampleEvent("Event2");
        CommandDecision result = new CommandDecision.Commutative(List.of(e1, e2));

        assertThat(result.events()).containsExactly(e1, e2);
    }

    // --- NonCommutative ---

    @Test
    @DisplayName("NonCommutative should carry events, decisionModel, and stream position")
    void nonCommutative_ShouldCarryAllFields() {
        AppendEvent event = sampleEvent("TestEvent");
        Query decisionModel = Query.forEventAndTag("TestEvent", "key", "value");
        StreamPosition streamPosition = StreamPosition.of(42L, Instant.now(), "tx-abc");

        CommandDecision.NonCommutative result = new CommandDecision.NonCommutative(
                List.of(event), decisionModel, streamPosition);

        assertThat(result.events()).containsExactly(event);
        assertThat(result.decisionModel()).isEqualTo(decisionModel);
        assertThat(result.streamPosition()).isEqualTo(streamPosition);
    }

    @Test
    @DisplayName("NonCommutative default events() method should return events")
    void nonCommutative_DefaultEventsMethod_ShouldReturnEvents() {
        AppendEvent event = sampleEvent("TestEvent");
        CommandDecision result = new CommandDecision.NonCommutative(
                List.of(event), Query.empty(), StreamPosition.zero());

        assertThat(result.events()).containsExactly(event);
    }

    // --- Idempotent ---

    @Test
    @DisplayName("Idempotent should carry events and tag metadata")
    void idempotent_ShouldCarryAllFields() {
        AppendEvent event = sampleEvent("WalletOpened");
        CommandDecision.Idempotent result = new CommandDecision.Idempotent(
                List.of(event), "WalletOpened", "wallet_id", "wallet-1");

        assertThat(result.events()).containsExactly(event);
        assertThat(result.eventType()).isEqualTo("WalletOpened");
        assertThat(result.tagKey()).isEqualTo("wallet_id");
        assertThat(result.tagValue()).isEqualTo("wallet-1");
    }

    @Test
    @DisplayName("Idempotent default events() method should return events")
    void idempotent_DefaultEventsMethod_ShouldReturnEvents() {
        AppendEvent event = sampleEvent("WalletOpened");
        CommandDecision result = new CommandDecision.Idempotent(
                List.of(event), "WalletOpened", "wallet_id", "wallet-1");

        assertThat(result.events()).containsExactly(event);
    }

    // --- Empty ---

    @Test
    @DisplayName("Empty should carry reason")
    void empty_ShouldCarryReason() {
        CommandDecision.NoOp result = new CommandDecision.NoOp("ALREADY_PROCESSED");

        assertThat(result.reason()).isEqualTo("ALREADY_PROCESSED");
        assertThat(result.events()).isEmpty();
    }

    @Test
    @DisplayName("NoOp.empty() should set reason to null")
    void empty_NoReason_ShouldSetReasonToNull() {
        CommandDecision.NoOp result = CommandDecision.NoOp.empty();

        assertThat(result.reason()).isNull();
        assertThat(result.events()).isEmpty();
    }

    @Test
    @DisplayName("Empty default events() method should return empty list")
    void empty_DefaultEventsMethod_ShouldReturnEmptyList() {
        CommandDecision result = new CommandDecision.NoOp("REASON");

        assertThat(result.events()).isEmpty();
    }

    // --- Sealed exhaustiveness ---

    @Test
    @DisplayName("Switch on sealed CommandDecision should be exhaustive")
    void sealedSwitch_ShouldBeExhaustive() {
        CommandDecision commutative = new CommandDecision.Commutative(List.of());
        CommandDecision nonCommutative = new CommandDecision.NonCommutative(List.of(), Query.empty(), StreamPosition.zero());
        CommandDecision idempotent = new CommandDecision.Idempotent(List.of(), "T", "k", "v");
        CommandDecision empty = CommandDecision.NoOp.empty();

        for (CommandDecision result : List.of(commutative, nonCommutative, idempotent, empty)) {
            String label = switch (result) {
                case CommandDecision.Commutative c -> "commutative";
                case CommandDecision.NonCommutative nc -> "non-commutative";
                case CommandDecision.Idempotent i -> "idempotent";
                case CommandDecision.NoOp e -> "empty";
            };
            assertThat(label).isNotNull();
        }
    }
}
