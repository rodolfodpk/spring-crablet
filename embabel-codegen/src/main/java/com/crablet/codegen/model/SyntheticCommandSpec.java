package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SyntheticCommandSpec(
        @JsonProperty("name")         String name,
        @JsonProperty("displayLabel") String displayLabel,
        @JsonProperty("note")         String note
) {}
