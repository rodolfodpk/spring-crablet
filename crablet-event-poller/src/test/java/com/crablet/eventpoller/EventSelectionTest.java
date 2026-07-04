package com.crablet.eventpoller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventSelection}'s static union helpers, which
 * {@link EventProcessorFactory#createProcessor} uses to derive a module-level filter from a
 * collection of per-processor selections (e.g. {@code TopicConfig}, {@code ViewSubscription},
 * {@code AutomationDefinition} values).
 */
@DisplayName("EventSelection union helpers Unit Tests")
class EventSelectionTest {

    private static EventSelection selection(
            Set<String> types, Set<String> required, Set<String> anyOf, Map<String, String> exact) {
        return new EventSelection() {
            @Override public Set<String> getEventTypes()   { return types; }
            @Override public Set<String> getRequiredTags() { return required; }
            @Override public Set<String> getAnyOfTags()    { return anyOf; }
            @Override public Map<String, String> getExactTags() { return exact; }
        };
    }

    @Test
    @DisplayName("Empty collection of selections yields unrestricted (empty) unions")
    void emptyCollection_yieldsUnrestricted() {
        List<EventSelection> selections = List.of();

        assertThat(EventSelection.unionEventTypes(selections)).isEmpty();
        assertThat(EventSelection.unionRequiredTags(selections)).isEmpty();
        assertThat(EventSelection.unionAnyOfTags(selections)).isEmpty();
        assertThat(EventSelection.unionExactTagKeys(selections)).isEmpty();
    }

    @Test
    @DisplayName("All-restricted selections union their event types and tag keys")
    void allRestricted_unionsCorrectly() {
        List<EventSelection> selections = List.of(
                selection(Set.of("WalletOpened"), Set.of("wallet_id"), Set.of("priority"), Map.of("region", "eu")),
                selection(Set.of("DepositMade"), Set.of("transfer_id"), Set.of("urgent"), Map.of("tier", "gold")));

        assertThat(EventSelection.unionEventTypes(selections)).containsExactlyInAnyOrder("WalletOpened", "DepositMade");
        assertThat(EventSelection.unionRequiredTags(selections)).containsExactlyInAnyOrder("wallet_id", "transfer_id");
        assertThat(EventSelection.unionAnyOfTags(selections)).containsExactlyInAnyOrder("priority", "urgent");
        assertThat(EventSelection.unionExactTagKeys(selections)).containsExactlyInAnyOrder("region", "tier");
    }

    @Test
    @DisplayName("One unrestricted selection disables the module-level filter for that dimension only")
    void oneUnrestrictedSelection_disablesOnlyThatDimension() {
        List<EventSelection> selections = List.of(
                selection(Set.of(), Set.of("wallet_id"), Set.of(), Map.of("region", "eu")),
                selection(Set.of("DepositMade"), Set.of("transfer_id"), Set.of("urgent"), Map.of("tier", "gold")));

        // eventTypes: first selection is unrestricted -> whole union is unrestricted
        assertThat(EventSelection.unionEventTypes(selections)).isEmpty();
        // requiredTags: both selections restrict -> union of both
        assertThat(EventSelection.unionRequiredTags(selections)).containsExactlyInAnyOrder("wallet_id", "transfer_id");
        // anyOfTags: first selection is unrestricted -> whole union is unrestricted
        assertThat(EventSelection.unionAnyOfTags(selections)).isEmpty();
        // exactTags: both selections restrict -> union of both keys
        assertThat(EventSelection.unionExactTagKeys(selections)).containsExactlyInAnyOrder("region", "tier");
    }
}
