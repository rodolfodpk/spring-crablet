package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeploymentSpec(
        @JsonProperty("topology") String topology,
        @JsonProperty("commandReplicas") int commandReplicas,
        @JsonProperty("keda") KedaSpec keda
) {
    public DeploymentSpec {
        if (topology == null || topology.isBlank()) topology = "monolith";
        if (commandReplicas <= 0) commandReplicas = 2;
        if (keda == null) keda = KedaSpec.defaults();
    }

    public static DeploymentSpec defaults() {
        return new DeploymentSpec("monolith", 2, KedaSpec.defaults());
    }

    public boolean isDistributed() {
        return "distributed".equalsIgnoreCase(topology);
    }
}
