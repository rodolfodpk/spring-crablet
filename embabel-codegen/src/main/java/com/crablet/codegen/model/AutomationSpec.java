package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AutomationSpec(
        @JsonProperty("name") String name,
        @JsonProperty("triggeredBy") String triggeredBy,
        @JsonProperty("emitsCommand") String emitsCommand,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("condition") String condition,
        @JsonProperty("readsView") String readsView
) {
    public AutomationSpec {
        pattern = (pattern == null) ? "todo-list" : pattern;
    }
}
