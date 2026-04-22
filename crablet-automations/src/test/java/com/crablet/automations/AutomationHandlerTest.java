package com.crablet.automations;

import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AutomationHandler} default methods.
 */
@DisplayName("AutomationHandler Default Methods")
class AutomationHandlerTest {

    private final AutomationHandler handler = new AutomationHandler() {
        @Override public String getAutomationName() { return "test"; }
        @Override public Set<String> getEventTypes() { return Set.of("SomeEvent"); }
        @Override public List<AutomationDecision> decide(StoredEvent event) { return List.of(); }
    };

    @Test
    @DisplayName("getRequiredTags should return empty set by default")
    void getRequiredTags_ShouldReturnEmptySet_ByDefault() {
        assertThat(handler.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("getAnyOfTags should return empty set by default")
    void getAnyOfTags_ShouldReturnEmptySet_ByDefault() {
        assertThat(handler.getAnyOfTags()).isEmpty();
    }
}
