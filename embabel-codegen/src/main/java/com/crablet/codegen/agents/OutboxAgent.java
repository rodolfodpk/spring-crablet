package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

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
        String outboxTemplate = templates.load("Outbox Publisher");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Never use fully qualified class names inline — use imports.

                Outbox publisher patterns to follow:
                %s

                Key rules:
                - Implement OutboxPublisher interface
                - publishBatch() iterates events and calls adapter for matching event types
                - Use ObjectMapper to deserialize each StoredEvent
                - For 'smtp' adapter: declare a delegating SmtpEmailService field with TODO constructor
                - For 'http' adapter: declare a RestClient/HttpClient field with TODO base URL
                - For 'kafka' adapter: declare a KafkaTemplate field (String, String)
                - For 'custom' adapter: add a comment for the developer to implement
                - getPreferredMode(): HTTP → INDIVIDUAL, Kafka → BATCH, others → INDIVIDUAL
                - isHealthy() should check the adapter's connectivity

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

                    Generate %s.java implementing OutboxPublisher.
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
