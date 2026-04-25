package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class OutboxAgent {

    private final AnthropicService anthropic;
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public OutboxAgent(AnthropicService anthropic, FileWriterTool fileWriter, TemplateLoader templates) {
        this.anthropic = anthropic;
        this.fileWriter = fileWriter;
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        if (model.outbox().isEmpty()) return;
        System.out.println("[OutboxAgent] Generating outbox publishers...");
        String outboxTemplate = templates.load("Outbox Publisher Interface");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Never use fully qualified class names inline — use imports.

                Outbox publisher patterns to follow:
                %s

                Key rules:
                - Generate a Java INTERFACE, not a @Component class.
                - Extend OutboxPublisher.
                - Declare two default methods: getName() and getPreferredMode().
                - Do NOT declare publishBatch() or isHealthy(). They must be implemented by the user.
                - Do NOT generate implementation classes or any @Component publisher files.
                - PublishMode is a nested enum on OutboxPublisher; it resolves as an inherited member type.
                  Do NOT import com.crablet.outbox.PublishMode — that standalone class does not exist.
                  Only import com.crablet.outbox.OutboxPublisher.
                - getPreferredMode() hint: HTTP → PublishMode.INDIVIDUAL, Kafka → PublishMode.BATCH, else INDIVIDUAL.
                - Javadoc: "Create a @Component class implementing this interface to provide publishBatch()
                  and isHealthy() logic." Do NOT mention clients, event iteration, retry, or adapter details.

                Output ONLY file blocks — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """.formatted(outboxTemplate);

        String outboxPkg = model.basePackage() + ".outbox";
        String domainPkg = model.basePackage() + ".domain";

        for (OutboxSpec outbox : model.outbox()) {
            String user = """
                    Domain: %s
                    Publisher name: %s
                    Package: %s
                    Domain package: %s
                    Topic: %s
                    Adapter type: %s
                    Handles events: %s

                    Generate %s.java as a Java interface extending OutboxPublisher.
                    """.formatted(
                    model.domain(),
                    outbox.name(),
                    outboxPkg,
                    domainPkg,
                    outbox.topic(),
                    outbox.adapter(),
                    String.join(", ", outbox.handles()),
                    outbox.name()
            );

            String response = anthropic.complete(system, user);
            fileWriter.writeGeneratedFiles(response, outputDir);
        }
    }
}
