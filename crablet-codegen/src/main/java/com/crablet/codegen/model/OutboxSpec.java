package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OutboxSpec(
        @JsonProperty("name") String name,
        @JsonProperty("topic") String topic,
        @JsonProperty("handles") List<String> handles,
        @JsonProperty("adapter") String adapter
) {
    public OutboxSpec {
        handles = (handles == null) ? List.of() : handles;
        adapter = (adapter == null) ? "custom" : adapter;
    }
}
