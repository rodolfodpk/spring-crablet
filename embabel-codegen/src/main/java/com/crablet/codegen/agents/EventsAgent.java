package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.stream.Collectors;

@Component
public class EventsAgent {

    private final AnthropicService anthropic;
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public EventsAgent(AnthropicService anthropic, FileWriterTool fileWriter, TemplateLoader templates) {
        this.anthropic = anthropic;
        this.fileWriter = fileWriter;
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        System.out.println("[EventsAgent] Generating sealed interface + event records...");
        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Generate idiomatic Java 25 code using records and sealed interfaces.

                Rules:
                - Use sealed interface for the event hierarchy
                - Each event is a Java record implementing the sealed interface
                - Use 'permits' clause listing all event types
                - Import statements at the top — never use fully qualified class names inline
                - No Lombok, no JPA annotations
                - No comments unless a constraint is non-obvious

                Output ONLY file blocks in this exact format — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """;

        String domainPkg = model.basePackage() + ".domain";
        String eventNames = model.events().stream()
                .map(EventSpec::name)
                .collect(Collectors.joining(", "));

        String user = """
                Domain: %s
                Base package: %s

                Generate:
                1. A sealed interface named %sEvent in package %s
                   permits: %s
                2. One Java record per event below, each implementing %sEvent.

                Events:
                %s
                """.formatted(
                model.domain(),
                domainPkg,
                model.domain(),
                domainPkg,
                eventNames,
                model.domain(),
                model.events().stream()
                        .map(e -> "  - " + e.name() + "(tags=" + e.tags() + ", fields=" + describeFields(e))
                        .collect(Collectors.joining("\n"))
        );

        String response = anthropic.complete(system, user);
        fileWriter.writeGeneratedFiles(response, outputDir);
    }

    private String describeFields(EventSpec e) {
        return e.fields().stream()
                .map(f -> f.name() + ":" + f.displayType())
                .collect(Collectors.joining(", "));
    }
}
