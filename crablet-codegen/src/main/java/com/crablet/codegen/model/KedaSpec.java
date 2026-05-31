package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KedaSpec(
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("minReplicas") int minReplicas,
        @JsonProperty("pollingInterval") int pollingInterval
) {
    public KedaSpec {
        if (minReplicas < 0) minReplicas = 0;
        if (pollingInterval <= 0) pollingInterval = 30;
    }

    public static KedaSpec defaults() {
        return new KedaSpec(false, 0, 30);
    }
}
