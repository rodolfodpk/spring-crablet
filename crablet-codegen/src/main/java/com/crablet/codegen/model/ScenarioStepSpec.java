package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScenarioStepSpec(
        @JsonProperty("keyword") String keyword,
        @JsonProperty("text") String text
) {
}
