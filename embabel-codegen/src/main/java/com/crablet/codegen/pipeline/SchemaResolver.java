package com.crablet.codegen.pipeline;

import com.crablet.codegen.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SchemaResolver {

    /**
     * Inlines every schema: reference in events and commands so that agents
     * always receive fully expanded FieldSpec lists.
     */
    public EventModel resolve(EventModel model) {
        if (model.schemas().isEmpty()) return model;
        Map<String, SchemaSpec> byName = model.schemas().stream()
                .collect(Collectors.toMap(SchemaSpec::name, s -> s));

        List<EventSpec> events = model.events().stream()
                .map(e -> resolveEvent(e, byName))
                .toList();
        List<CommandSpec> commands = model.commands().stream()
                .map(c -> resolveCommand(c, byName))
                .toList();

        return new EventModel(
                model.domain(),
                model.basePackage(),
                model.schemas(),
                events,
                commands,
                model.views(),
                model.automations(),
                model.outbox()
        );
    }

    private EventSpec resolveEvent(EventSpec e, Map<String, SchemaSpec> schemas) {
        if (e.schema() == null || !schemas.containsKey(e.schema())) return e;
        List<FieldSpec> merged = merge(schemas.get(e.schema()).fields(), e.fields());
        return new EventSpec(e.name(), e.tags(), null, merged);
    }

    private CommandSpec resolveCommand(CommandSpec c, Map<String, SchemaSpec> schemas) {
        if (c.schema() == null || !schemas.containsKey(c.schema())) return c;
        List<FieldSpec> merged = merge(schemas.get(c.schema()).fields(), c.fields());
        return new CommandSpec(c.name(), c.pattern(), c.produces(), c.guardEvents(), null, merged, c.validation());
    }

    private List<FieldSpec> merge(List<FieldSpec> base, List<FieldSpec> overrides) {
        Map<String, FieldSpec> result = base.stream()
                .collect(Collectors.toMap(FieldSpec::name, f -> f));
        overrides.forEach(f -> result.put(f.name(), f));
        return List.copyOf(result.values());
    }
}
