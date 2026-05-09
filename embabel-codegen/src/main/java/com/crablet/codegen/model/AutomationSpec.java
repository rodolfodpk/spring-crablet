package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AutomationSpec(
        @JsonProperty("name") String name,
        @JsonProperty("triggeredBy") String triggeredBy,
        @JsonProperty("emitsCommand") String emitsCommand,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("condition") String condition,
        @JsonProperty("readsView") String readsView,
        @JsonProperty("readsViews") List<String> readsViews,
        @JsonProperty("wakeEventsExtra") List<String> wakeEventsExtra,
        @JsonProperty("wakeEventsExclude") List<String> wakeEventsExclude
) {
    public AutomationSpec {
        pattern = (pattern == null) ? "todo-list" : pattern;
        readsViews = (readsViews == null) ? legacyReadsViews(readsView) : List.copyOf(readsViews);
        wakeEventsExtra = (wakeEventsExtra == null) ? List.of() : List.copyOf(wakeEventsExtra);
        wakeEventsExclude = (wakeEventsExclude == null) ? List.of() : List.copyOf(wakeEventsExclude);
    }

    public AutomationSpec(
            String name,
            String triggeredBy,
            String emitsCommand,
            String pattern,
            String condition,
            String readsView) {
        this(name, triggeredBy, emitsCommand, pattern, condition, readsView, null, null, null);
    }

    private static List<String> legacyReadsViews(String readsView) {
        return (readsView == null || readsView.isBlank()) ? List.of() : List.of(readsView);
    }
}
