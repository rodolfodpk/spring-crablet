package com.crablet.codegen.planning;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.model.ViewSpec;
import com.crablet.codegen.pipeline.SchemaResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArtifactPlanner {

    private final SchemaResolver schemaResolver;

    public ArtifactPlanner(SchemaResolver schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public List<PlannedArtifact> plan(EventModel model) {
        EventModel resolved = schemaResolver.resolve(model);
        List<PlannedArtifact> artifacts = new ArrayList<>();

        String domainPackage = resolved.basePackage() + ".domain";
        artifacts.add(PlannedArtifact.javaClass("domain", domainPackage, resolved.domain() + "Event"));
        for (EventSpec event : resolved.events()) {
            artifacts.add(PlannedArtifact.javaClass("domain", domainPackage, event.name()));
        }

        String commandPackage = resolved.basePackage() + ".command";
        artifacts.add(PlannedArtifact.javaClass("command", commandPackage, resolved.domain() + "State"));
        artifacts.add(PlannedArtifact.javaClass("command", commandPackage, resolved.domain() + "StateProjector"));
        artifacts.add(PlannedArtifact.javaClass("command", commandPackage, resolved.domain() + "QueryPatterns"));
        for (CommandSpec command : resolved.commands()) {
            artifacts.add(PlannedArtifact.javaClass("command", commandPackage, command.name()));
            artifacts.add(PlannedArtifact.javaClass("command", commandPackage, command.name() + "CommandHandler"));
        }

        String viewPackage = resolved.basePackage() + ".view";
        int migrationNumber = 100;
        for (ViewSpec view : resolved.views()) {
            artifacts.add(PlannedArtifact.javaClass("view", viewPackage, view.name() + "ViewProjector"));
            artifacts.add(PlannedArtifact.migration(
                    "view",
                    "V" + migrationNumber + "__create_" + view.tableName() + ".sql",
                    view.tableName()
            ));
            migrationNumber++;
        }

        String automationPackage = resolved.basePackage() + ".automation";
        for (AutomationSpec automation : resolved.automations()) {
            artifacts.add(PlannedArtifact.javaClass(
                    "automation",
                    automationPackage,
                    toJavaIdentifier(automation.name()) + "AutomationHandler"
            ));
        }

        String outboxPackage = resolved.basePackage() + ".outbox";
        for (OutboxSpec outbox : resolved.outbox()) {
            artifacts.add(PlannedArtifact.javaClass(
                    "outbox",
                    outboxPackage,
                    toJavaIdentifier(outbox.name())
            ));
        }

        return artifacts;
    }

    public String render(EventModel model) {
        List<PlannedArtifact> artifacts = plan(model);
        StringBuilder out = new StringBuilder();
        out.append("Planned artifacts for ")
                .append(model.domain())
                .append(" (")
                .append(model.basePackage())
                .append(")\n");

        String section = "";
        for (PlannedArtifact artifact : artifacts) {
            if (!artifact.section().equals(section)) {
                section = artifact.section();
                out.append("\n").append(title(section)).append(":\n");
            }
            out.append("- ").append(artifact.displayName());
            if (artifact.detail() != null && !artifact.detail().isBlank()) {
                out.append(" (").append(artifact.detail()).append(")");
            }
            out.append("\n");
        }
        return out.toString();
    }

    private String title(String section) {
        return switch (section) {
            case "domain" -> "Domain";
            case "command" -> "Commands";
            case "view" -> "Views";
            case "automation" -> "Automations";
            case "outbox" -> "Outbox";
            default -> section;
        };
    }

    private String toJavaIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "Generated";
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = true;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }
        if (result.isEmpty()) {
            return "Generated";
        }
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, "Generated");
        }
        return result.toString();
    }
}
