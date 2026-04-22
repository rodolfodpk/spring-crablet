package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.model.ViewSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class ViewsAgent {

    private final AnthropicService anthropic;
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public ViewsAgent(AnthropicService anthropic, FileWriterTool fileWriter, TemplateLoader templates) {
        this.anthropic = anthropic;
        this.fileWriter = fileWriter;
        this.templates = templates;
    }

    public void generate(EventModel model, Path outputDir) {
        System.out.println("[ViewsAgent] Generating view projectors and SQL migrations...");
        String viewTemplate = templates.load("View Projector");

        String system = """
                You are a Java code generator for the spring-crablet event sourcing framework.
                Never use fully qualified class names inline — use imports.

                View projector patterns to follow:
                %s

                Key rules:
                - Extend AbstractTypedViewProjector<SealedEventInterface>
                - handleEvent() uses switch + pattern matching over sealed interface variants
                - Use ON CONFLICT DO UPDATE for upserts (idempotency)
                - The sealed event interface is in the domain package
                - Inject WriteDataSource (NOT ReadDataSource) in the constructor
                - For SQL migrations use Flyway convention: V{n}__create_{table_name}.sql

                Output ONLY file blocks — no prose, no markdown:
                ===FILE: relative/path/to/ClassName.java===
                <complete file content>
                ===END FILE===
                """.formatted(viewTemplate);

        AtomicInteger migration = new AtomicInteger(100);

        for (ViewSpec view : model.views()) {
            String viewPkg = model.basePackage() + ".view";
            String domainPkg = model.basePackage() + ".domain";
            int migNum = migration.getAndIncrement();

            String user = """
                    Domain: %s
                    View name: %s
                    Package: %s
                    Domain package (events live here): %s
                    Sealed event interface: %sEvent
                    Table name: %s
                    Tag filter (primary tag): %s
                    Reads events: %s
                    Migration number: V%d

                    Fields in view table:
                    %s

                    Generate:
                    1. %sViewProjector.java (extends AbstractTypedViewProjector<%sEvent>)
                    2. V%d__create_%s.sql (Flyway migration creating the view table)
                    """.formatted(
                    model.domain(),
                    view.name(),
                    viewPkg,
                    domainPkg,
                    model.domain(),
                    view.tableName(),
                    view.tag(),
                    String.join(", ", view.reads()),
                    migNum,
                    view.fields().stream()
                            .map(f -> "  " + f.name() + " " + sqlType(f))
                            .collect(Collectors.joining("\n")),
                    view.name(),
                    model.domain(),
                    migNum,
                    view.tableName()
            );

            String response = anthropic.complete(system, user);
            fileWriter.writeGeneratedFiles(response, outputDir);
        }
    }

    private String sqlType(FieldSpec f) {
        if ("array".equals(f.type())) {
            return switch (f.items() != null ? f.items().type() : "string") {
                case "integer", "int" -> "INTEGER[]";
                case "long"           -> "BIGINT[]";
                case "UUID"           -> "UUID[]";
                default               -> "TEXT[]";
            };
        }
        if ("map".equals(f.type())) return "JSONB";
        return switch (f.type()) {
            case "integer", "int" -> "INTEGER";
            case "long"           -> "BIGINT";
            case "boolean"        -> "BOOLEAN";
            case "number", "BigDecimal" -> "NUMERIC(19,4)";
            case "UUID"           -> "UUID";
            case "Instant"        -> "TIMESTAMP WITH TIME ZONE";
            default               -> "TEXT";
        };
    }
}
