package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ScenarioSpec(
        @JsonProperty("name") String name,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("steps") List<ScenarioStepSpec> steps
) {
    public ScenarioSpec {
        tags = (tags == null) ? List.of() : tags;
        steps = (steps == null) ? List.of() : steps;
    }
}
