package com.crablet.codegen.planning;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.model.ScenarioSpec;
import com.crablet.codegen.model.ViewSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ModelValidator {

    public void validate(EventModel model) {
        List<String> errors = errors(model);
        if (!errors.isEmpty()) {
            throw new ModelValidationException(errors);
        }
    }

    public List<String> errors(EventModel model) {
        var errors = new ArrayList<String>();

        requireName(errors, "domain", model.domain());
        requireName(errors, "basePackage", model.basePackage());

        validateEvents(model, errors);
        validateCommands(model, errors);
        validateViews(model, errors);
        validateAutomations(model, errors);
        validateOutbox(model, errors);
        validateScenarios(model, errors);

        return List.copyOf(errors);
    }

    private void validateEvents(EventModel model, List<String> errors) {
        if (model.events().isEmpty()) {
            errors.add("events must contain at least one event");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (EventSpec event : model.events()) {
            if (isBlank(event.name())) {
                errors.add("events contains an event without a name");
                continue;
            }
            if (!seen.add(event.name())) {
                errors.add("events contains duplicate event '" + event.name() + "'");
            }
            if (event.fields().isEmpty()) {
                errors.add("event '" + event.name() + "' must declare fields");
            }
            if (event.tags().isEmpty()) {
                errors.add("event '" + event.name() + "' must declare at least one tag");
            }
        }
    }

    private void validateCommands(EventModel model, List<String> errors) {
        if (model.commands().isEmpty()) {
            errors.add("commands must contain at least one command");
            return;
        }
        Set<String> events = Set.copyOf(model.eventNames());
        Set<String> seen = new HashSet<>();
        for (CommandSpec command : model.commands()) {
            if (isBlank(command.name())) {
                errors.add("commands contains a command without a name");
                continue;
            }
            if (!seen.add(command.name())) {
                errors.add("commands contains duplicate command '" + command.name() + "'");
            }
            if (!command.isIdempotent() && !command.isCommutative() && !command.isNonCommutative()) {
                errors.add("command '" + command.name() + "' must use pattern idempotent, commutative, or non-commutative");
            }
            if (command.fields().isEmpty()) {
                errors.add("command '" + command.name() + "' must declare fields");
            }
            if (command.produces().isEmpty()) {
                errors.add("command '" + command.name() + "' must declare produced events");
            }
            for (String event : command.produces()) {
                if (!events.contains(event)) {
                    errors.add("command '" + command.name() + "' produces unknown event '" + event + "'");
                }
            }
            for (String guardEvent : command.guardEvents()) {
                if (!events.contains(guardEvent)) {
                    errors.add("command '" + command.name() + "' guards on unknown event '" + guardEvent + "'");
                }
            }
        }
    }

    private void validateViews(EventModel model, List<String> errors) {
        Set<String> eventNames = Set.copyOf(model.eventNames());
        Set<String> seen = new HashSet<>();
        for (ViewSpec view : model.views()) {
            if (isBlank(view.name())) {
                errors.add("views contains a view without a name");
                continue;
            }
            if (!seen.add(view.name())) {
                errors.add("views contains duplicate view '" + view.name() + "'");
            }
            if (view.reads().isEmpty()) {
                errors.add("view '" + view.name() + "' must declare read events");
            }
            for (String readEvent : view.reads()) {
                if (!eventNames.contains(readEvent)) {
                    errors.add("view '" + view.name() + "' reads unknown event '" + readEvent + "'");
                }
            }
            if (isBlank(view.tag())) {
                errors.add("view '" + view.name() + "' must declare a tag");
            } else if (view.reads().stream().noneMatch(eventName -> eventHasTag(model, eventName, view.tag()))) {
                errors.add("view '" + view.name() + "' tag '" + view.tag() + "' is not present on any read event");
            }
            if (view.fields().isEmpty()) {
                errors.add("view '" + view.name() + "' must declare fields");
            }
        }
    }

    private void validateAutomations(EventModel model, List<String> errors) {
        Set<String> eventNames = Set.copyOf(model.eventNames());
        Set<String> commandNames = model.commands().stream().map(CommandSpec::name).collect(Collectors.toSet());
        Set<String> viewNames = model.views().stream().map(ViewSpec::name).collect(Collectors.toSet());
        for (AutomationSpec automation : model.automations()) {
            if (isBlank(automation.name())) {
                errors.add("automations contains an automation without a name");
            }
            if (!isBlank(automation.triggeredBy()) && !eventNames.contains(automation.triggeredBy())) {
                errors.add("automation '" + automation.name() + "' is triggered by unknown event '" + automation.triggeredBy() + "'");
            }
            if (!isBlank(automation.emitsCommand()) && !commandNames.contains(automation.emitsCommand())) {
                errors.add("automation '" + automation.name() + "' emits unknown command '" + automation.emitsCommand() + "'");
            }
            for (String view : automation.readsViews()) {
                if (!viewNames.contains(view)) {
                    errors.add("automation '" + automation.name() + "' reads unknown view '" + view + "'");
                }
            }
        }
    }

    private void validateOutbox(EventModel model, List<String> errors) {
        Set<String> eventNames = Set.copyOf(model.eventNames());
        for (OutboxSpec outbox : model.outbox()) {
            if (isBlank(outbox.name())) {
                errors.add("outbox contains a publisher without a name");
            }
            if (isBlank(outbox.topic())) {
                errors.add("outbox publisher '" + outbox.name() + "' must declare a topic");
            }
            if (outbox.handles().isEmpty()) {
                errors.add("outbox publisher '" + outbox.name() + "' must declare handled events");
            }
            for (String event : outbox.handles()) {
                if (!eventNames.contains(event)) {
                    errors.add("outbox publisher '" + outbox.name() + "' handles unknown event '" + event + "'");
                }
            }
        }
    }

    private void validateScenarios(EventModel model, List<String> errors) {
        for (ScenarioSpec scenario : model.scenarios()) {
            if (isBlank(scenario.name())) {
                errors.add("scenarios contains a scenario without a name");
                continue;
            }
            if (scenario.steps().isEmpty()) {
                errors.add("scenario '" + scenario.name() + "' must declare steps");
            }
        }
    }

    private boolean eventHasTag(EventModel model, String eventName, String tag) {
        return model.events().stream()
                .filter(event -> event.name().equals(eventName))
                .anyMatch(event -> event.tags().contains(tag));
    }

    private void requireName(List<String> errors, String field, String value) {
        if (isBlank(value)) {
            errors.add(field + " is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
