package com.crablet.automations;

import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ViewBackedAutomationHandler Default Methods")
class ViewBackedAutomationHandlerTest {

    private final ViewBackedAutomationHandler handler = new ViewBackedAutomationHandler() {
        @Override public String getAutomationName() { return "test-automation"; }
        @Override public Set<String> getReadViewNames() { return Set.of("some_view"); }
        @Override public List<AutomationDecision> decide(StoredEvent event) { return List.of(); }
    };

    @Test
    @DisplayName("getEventTypes throws UnsupportedOperationException")
    void getEventTypes_Throws() {
        assertThatThrownBy(handler::getEventTypes)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("resolved from view subscriptions at startup");
    }

    @Test
    @DisplayName("getWakeEventsExtra returns empty set by default")
    void getWakeEventsExtra_EmptyByDefault() {
        assertThat(handler.getWakeEventsExtra()).isEmpty();
    }

    @Test
    @DisplayName("getWakeEventsExclude returns empty set by default")
    void getWakeEventsExclude_EmptyByDefault() {
        assertThat(handler.getWakeEventsExclude()).isEmpty();
    }

    @Test
    @DisplayName("getRequiredTags returns empty set by default")
    void getRequiredTags_EmptyByDefault() {
        assertThat(handler.getRequiredTags()).isEmpty();
    }

    @Test
    @DisplayName("getAnyOfTags returns empty set by default")
    void getAnyOfTags_EmptyByDefault() {
        assertThat(handler.getAnyOfTags()).isEmpty();
    }
}
