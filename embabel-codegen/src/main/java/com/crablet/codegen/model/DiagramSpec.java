package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagramSpec(
        @JsonProperty("actors")            List<ActorSpec> actors,
        @JsonProperty("lanes")             List<LaneSpec> lanes,
        @JsonProperty("assignments")       Map<String, String> assignments,
        @JsonProperty("triggers")          List<TriggerSpec> triggers,
        @JsonProperty("syntheticCommands") List<SyntheticCommandSpec> syntheticCommands,
        @JsonProperty("eventBadges")       Map<String, String> eventBadges,
        @JsonProperty("automations")       List<AutomationSpec> automations
) {
    public DiagramSpec {
        actors            = (actors            == null) ? List.of() : actors;
        lanes             = (lanes             == null) ? List.of() : lanes;
        assignments       = (assignments       == null) ? Map.of()  : assignments;
        triggers          = (triggers          == null) ? List.of() : triggers;
        syntheticCommands = (syntheticCommands == null) ? List.of() : syntheticCommands;
        eventBadges       = (eventBadges       == null) ? Map.of()  : eventBadges;
        automations       = (automations       == null) ? List.of() : automations;
    }

    public static DiagramSpec empty() {
        return new DiagramSpec(null, null, null, null, null, null, null);
    }
}
