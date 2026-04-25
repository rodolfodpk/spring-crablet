package com.crablet.codegen.agents;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

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
        String automationTemplate = templates.load("Automation Handler Interface");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Never use fully qualified class names inline — use imports.

                Automation handler patterns to follow:
                %s

                Key rules:
                - Generate a Java INTERFACE, not a @Component class.
                - Extend AutomationHandler.
                - Declare three default methods: getAutomationName(), getEventTypes(), getRequiredTags().
                - Do NOT declare decide(). It is inherited and must be implemented by the user.
                - Do NOT generate implementation classes or any @Component handler files.
                - getRequiredTags() must always return Set.of(). AutomationSpec has no requiredTags field;
                  if non-empty is ever needed, use string literals, NOT tag constant class references.
                - Javadoc: "Create a @Component class implementing this interface to provide decide() logic."
                  Do NOT mention condition translation, JdbcTemplate, ObjectMapper, or emitted command mapping.

                Output ONLY file blocks — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """.formatted(automationTemplate);

        String automationPkg = model.basePackage() + ".automation";
        String commandPkg = model.basePackage() + ".command";
        String domainPkg = model.basePackage() + ".domain";

        for (AutomationSpec automation : model.automations()) {
            String user = """
                    Domain: %s
                    Automation name: %s
                    Package: %s
                    Domain package: %s
                    Command package: %s

                    Trigger event: %s
                    Emits command: %s

                    Generate %sAutomationHandler.java as a Java interface extending AutomationHandler.
                    """.formatted(
                    model.domain(),
                    automation.name(),
                    automationPkg,
                    domainPkg,
                    commandPkg,
                    automation.triggeredBy(),
                    automation.emitsCommand(),
                    automation.name()
            );

            String response = anthropic.complete(system, user);
            fileWriter.writeGeneratedFiles(response, outputDir);
        }
    }
}
