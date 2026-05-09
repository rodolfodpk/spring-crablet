package com.crablet.automations.internal;

import com.crablet.automations.AutomationDecision;
import com.crablet.automations.AutomationDefinition;
import com.crablet.automations.AutomationHandler;
import com.crablet.automations.ViewBackedAutomationHandler;
import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NullAway")
@DisplayName("AutomationDefinitionResolver Unit Tests")
class AutomationDefinitionResolverTest {

    private static AutomationHandler plainHandler(String name, String... eventTypes) {
        return new AutomationHandler() {
            @Override public String getAutomationName() { return name; }
            @Override public Set<String> getEventTypes() { return Set.of(eventTypes); }
            @Override public List<AutomationDecision> decide(StoredEvent event) { return List.of(); }
        };
    }

    private static ViewBackedAutomationHandler viewBackedHandler(String name, Set<String> readViews) {
        return viewBackedHandler(name, readViews, Set.of(), Set.of());
    }

    private static ViewBackedAutomationHandler viewBackedHandler(
            String name, Set<String> readViews, Set<String> extra, Set<String> exclude) {
        return new ViewBackedAutomationHandler() {
            @Override public String getAutomationName() { return name; }
            @Override public Set<String> getReadViewNames() { return readViews; }
            @Override public Set<String> getWakeEventsExtra() { return extra; }
            @Override public Set<String> getWakeEventsExclude() { return exclude; }
            @Override public List<AutomationDecision> decide(StoredEvent event) { return List.of(); }
        };
    }

    private static ViewSubscriptionLookup lookup(Map<String, Set<String>> views) {
        return viewName -> Optional.ofNullable(views.get(viewName));
    }

    @Test
    @DisplayName("plain handler passes through unchanged")
    void plainHandler_PassesThrough() {
        AutomationHandler handler = plainHandler("my-automation", "OrderPlaced");
        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("my-automation", handler), Optional.empty()).resolve();

        assertThat(result).containsKey("my-automation");
        assertThat(result.get("my-automation").getEventTypes()).containsExactly("OrderPlaced");
    }

    @Test
    @DisplayName("view-backed handler resolves event types from single view")
    void viewBacked_SingleView_ResolvesEventTypes() {
        ViewBackedAutomationHandler handler = viewBackedHandler("enroll-automation", Set.of("todo_view"));
        ViewSubscriptionLookup vl = lookup(Map.of("todo_view", Set.of("CourseCreated", "StudentRegistered")));

        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("enroll-automation", handler), Optional.of(vl)).resolve();

        assertThat(result.get("enroll-automation").getEventTypes())
                .containsExactlyInAnyOrder("CourseCreated", "StudentRegistered");
    }

    @Test
    @DisplayName("view-backed handler resolves union from multiple views")
    void viewBacked_MultipleViews_UnionOfEventTypes() {
        ViewBackedAutomationHandler handler = viewBackedHandler("combo-automation",
                Set.of("view_a", "view_b"));
        ViewSubscriptionLookup vl = lookup(Map.of(
                "view_a", Set.of("EventA", "EventB"),
                "view_b", Set.of("EventB", "EventC")));

        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("combo-automation", handler), Optional.of(vl)).resolve();

        assertThat(result.get("combo-automation").getEventTypes())
                .containsExactlyInAnyOrder("EventA", "EventB", "EventC");
    }

    @Test
    @DisplayName("wakeEventsExtra adds events beyond those inferred from views")
    void wakeEventsExtra_AddsToInferredSet() {
        ViewBackedAutomationHandler handler = viewBackedHandler("my-automation",
                Set.of("todo_view"), Set.of("BonusEvent"), Set.of());
        ViewSubscriptionLookup vl = lookup(Map.of("todo_view", Set.of("ViewEvent")));

        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("my-automation", handler), Optional.of(vl)).resolve();

        assertThat(result.get("my-automation").getEventTypes())
                .containsExactlyInAnyOrder("ViewEvent", "BonusEvent");
    }

    @Test
    @DisplayName("wakeEventsExclude removes events from the inferred set")
    void wakeEventsExclude_RemovesFromInferredSet() {
        ViewBackedAutomationHandler handler = viewBackedHandler("my-automation",
                Set.of("todo_view"), Set.of(), Set.of("NoisyEvent"));
        ViewSubscriptionLookup vl = lookup(Map.of("todo_view", Set.of("UsefulEvent", "NoisyEvent")));

        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("my-automation", handler), Optional.of(vl)).resolve();

        assertThat(result.get("my-automation").getEventTypes()).containsExactly("UsefulEvent");
    }

    @Test
    @DisplayName("missing ViewSubscriptionLookup with view-backed handler fails startup")
    void missingLookup_WithViewBackedHandler_FailsStartup() {
        ViewBackedAutomationHandler handler = viewBackedHandler("my-automation", Set.of("some_view"));

        assertThatThrownBy(() -> new AutomationDefinitionResolver(
                Map.of("my-automation", handler), Optional.empty()).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("my-automation")
                .hasMessageContaining("crablet-views");
    }

    @Test
    @DisplayName("referenced view not found in lookup fails startup with automation and view name")
    void missingView_FailsStartupWithBothNames() {
        ViewBackedAutomationHandler handler = viewBackedHandler("enroll-automation", Set.of("missing_view"));
        ViewSubscriptionLookup vl = lookup(Map.of("other_view", Set.of("SomeEvent")));

        assertThatThrownBy(() -> new AutomationDefinitionResolver(
                Map.of("enroll-automation", handler), Optional.of(vl)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enroll-automation")
                .hasMessageContaining("missing_view");
    }

    @Test
    @DisplayName("referenced view with empty event types fails startup")
    void emptyViewEventTypes_FailsStartup() {
        ViewBackedAutomationHandler handler = viewBackedHandler("my-automation", Set.of("empty_view"));
        ViewSubscriptionLookup vl = lookup(Map.of("empty_view", Set.of()));

        assertThatThrownBy(() -> new AutomationDefinitionResolver(
                Map.of("my-automation", handler), Optional.of(vl)).resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("my-automation")
                .hasMessageContaining("empty_view");
    }

    @Test
    @DisplayName("resolved definition delegates name and tag methods to original handler")
    void resolvedDefinition_DelegatesMetadataToOriginal() {
        ViewBackedAutomationHandler handler = new ViewBackedAutomationHandler() {
            @Override public String getAutomationName() { return "tagged-automation"; }
            @Override public Set<String> getReadViewNames() { return Set.of("some_view"); }
            @Override public Set<String> getRequiredTags() { return Set.of("tenant_id"); }
            @Override public Set<String> getAnyOfTags() { return Set.of("region"); }
            @Override public List<AutomationDecision> decide(StoredEvent event) { return List.of(); }
        };
        ViewSubscriptionLookup vl = lookup(Map.of("some_view", Set.of("SomeEvent")));

        Map<String, AutomationDefinition> result = new AutomationDefinitionResolver(
                Map.of("tagged-automation", handler), Optional.of(vl)).resolve();

        AutomationDefinition def = result.get("tagged-automation");
        assertThat(def.getAutomationName()).isEqualTo("tagged-automation");
        assertThat(def.getRequiredTags()).containsExactly("tenant_id");
        assertThat(def.getAnyOfTags()).containsExactly("region");
    }
}
