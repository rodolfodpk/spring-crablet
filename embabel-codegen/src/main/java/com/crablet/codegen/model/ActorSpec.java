package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActorSpec(
        @JsonProperty("id")    String id,
        @JsonProperty("label") String label
) {}
