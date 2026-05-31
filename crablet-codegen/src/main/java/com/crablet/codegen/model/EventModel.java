package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record EventModel(
        @JsonProperty("domain") String domain,
        @JsonProperty("basePackage") String basePackage,
        @JsonProperty("schemas") List<SchemaSpec> schemas,
        @JsonProperty("events") List<EventSpec> events,
        @JsonProperty("commands") List<CommandSpec> commands,
        @JsonProperty("views") List<ViewSpec> views,
        @JsonProperty("automations") List<AutomationSpec> automations,
        @JsonProperty("outbox") List<OutboxSpec> outbox,
        @JsonProperty("scenarios") List<ScenarioSpec> scenarios,
        @JsonProperty("deployment") DeploymentSpec deployment,
        @JsonProperty("diagram") DiagramSpec diagram
) {
    public EventModel {
        schemas = (schemas == null) ? List.of() : schemas;
        events = (events == null) ? List.of() : events;
        commands = (commands == null) ? List.of() : commands;
        views = (views == null) ? List.of() : views;
        automations = (automations == null) ? List.of() : automations;
        outbox = (outbox == null) ? List.of() : outbox;
        scenarios = (scenarios == null) ? List.of() : scenarios;
        deployment = (deployment == null) ? DeploymentSpec.defaults() : deployment;
        diagram = (diagram == null) ? DiagramSpec.empty() : diagram;
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

    public List<String> automationWakeEvents(AutomationSpec automation) {
        Set<String> wakeEvents = new LinkedHashSet<>();
        addIfPresent(wakeEvents, automation.triggeredBy());
        for (String viewName : automation.readsViews()) {
            wakeEvents.addAll(viewNamed(viewName).reads());
        }
        automation.wakeEventsExtra().forEach(event -> addIfPresent(wakeEvents, event));
        automation.wakeEventsExclude().forEach(wakeEvents::remove);
        return List.copyOf(wakeEvents);
    }

    public SchemaSpec schemaNamed(String name) {
        return schemas.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Schema not found: " + name));
    }

    private static void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }
}
