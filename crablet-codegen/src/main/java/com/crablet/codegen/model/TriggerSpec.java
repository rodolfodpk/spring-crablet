package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TriggerSpec(
        @JsonProperty("name")          String name,
        @JsonProperty("linkedCommand") String linkedCommand,
        @JsonProperty("actor")         String actor
) {}
