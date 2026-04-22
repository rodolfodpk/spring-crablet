package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EventModel(
        @JsonProperty("domain") String domain,
        @JsonProperty("basePackage") String basePackage,
        @JsonProperty("schemas") List<SchemaSpec> schemas,
        @JsonProperty("events") List<EventSpec> events,
        @JsonProperty("commands") List<CommandSpec> commands,
        @JsonProperty("views") List<ViewSpec> views,
        @JsonProperty("automations") List<AutomationSpec> automations,
        @JsonProperty("outbox") List<OutboxSpec> outbox
) {
    public EventModel {
        schemas = (schemas == null) ? List.of() : schemas;
        events = (events == null) ? List.of() : events;
        commands = (commands == null) ? List.of() : commands;
        views = (views == null) ? List.of() : views;
        automations = (automations == null) ? List.of() : automations;
        outbox = (outbox == null) ? List.of() : outbox;
    }

    public List<String> eventNames() {
        return events.stream().map(EventSpec::name).toList();
    }

    public ViewSpec viewNamed(String name) {
        return views.stream()
                .filter(v -> v.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("View not found: " + name));
    }

    public SchemaSpec schemaNamed(String name) {
        return schemas.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Schema not found: " + name));
    }
}
