package com.crablet.codegen.agents;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.ViewSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

@Component
public class AutomationsAgent {

    private final AnthropicService anthropic;
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public AutomationsAgent(AnthropicService anthropic, FileWriterTool fileWriter, TemplateLoader templates) {
        this.anthropic = anthropic;
        this.fileWriter = fileWriter;
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        if (model.automations().isEmpty()) return;
        System.out.println("[AutomationsAgent] Generating automation handlers...");
        String automationTemplate = templates.load("Automation Handler");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Never use fully qualified class names inline — use imports.

                Automation handler patterns to follow:
                %s

                Key rules:
                - Implement AutomationHandler interface
                - getEventTypes() returns Set.of(type(TriggerEvent.class))
                - getRequiredTags() returns the tag key from the trigger event
                - decide() receives a StoredEvent and returns List<AutomationDecision>
                - Use JdbcTemplate to read the named view table when readsView is set
                - Translate condition: expression into a Java if-statement
                - Return AutomationDecision.ExecuteCommand when condition met, AutomationDecision.NoOp otherwise
                - Use ObjectMapper to deserialize the stored event

                Output ONLY file blocks — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """.formatted(automationTemplate);

        String automationPkg = model.basePackage() + ".automation";
        String commandPkg = model.basePackage() + ".command";
        String domainPkg = model.basePackage() + ".domain";

        for (AutomationSpec automation : model.automations()) {
            ViewSpec viewSpec = automation.readsView() != null && !automation.readsView().isBlank()
                    ? model.viewNamed(automation.readsView())
                    : null;

            String user = """
                    Domain: %s
                    Automation name: %s
                    Package: %s
                    Domain package: %s
                    Command package: %s

                    Trigger event: %s
                    Emits command: %s
                    Condition: %s
                    %s

                    Generate %sAutomationHandler.java implementing AutomationHandler.
                    """.formatted(
                    model.domain(),
                    automation.name(),
                    automationPkg,
                    domainPkg,
                    commandPkg,
                    automation.triggeredBy(),
                    automation.emitsCommand(),
                    automation.condition() != null ? automation.condition() : "always",
                    viewSpec != null
                            ? "Reads view table: " + viewSpec.tableName()
                            + "\nView fields: " + viewSpec.fields().stream()
                            .map(f -> f.name() + ":" + f.type()).collect(Collectors.joining(", "))
                            : "No view lookup needed",
                    automation.name()
            );

            String response = anthropic.complete(system, user);
            fileWriter.writeGeneratedFiles(response, outputDir);
        }
    }
}
