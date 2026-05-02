package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LaneSpec(
        @JsonProperty("id")    String id,
        @JsonProperty("label") String label
) {}
