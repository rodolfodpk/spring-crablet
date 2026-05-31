package com.crablet.codegen.generator;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.tools.FileWriterTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class OutboxGenerator {

    private final FileWriterTool fileWriter;

    public OutboxGenerator(FileWriterTool fileWriter) {
        this.fileWriter = fileWriter;
    }

    public void generate(EventModel model, Path outputDir) {
        if (model.outbox().isEmpty()) return;
        System.out.println("[OutboxGenerator] Generating outbox contracts...");

        String outboxPkg = model.basePackage() + ".outbox";

        for (OutboxSpec outbox : model.outbox()) {
            fileWriter.writeGeneratedFile(
                    JavaRenderSupport.packagePath(outboxPkg, outbox.name()),
                    renderOutbox(outboxPkg, outbox),
                    outputDir
            );
        }
    }

    private String renderOutbox(String outboxPkg, OutboxSpec outbox) {
        String preferredMode = "kafka".equalsIgnoreCase(outbox.adapter()) ? "BATCH" : "INDIVIDUAL";
        return """
                package %s;

                import com.crablet.outbox.OutboxPublisher;

                %s/**
                 * Create a {@code @Component} class implementing this interface to provide publishBatch()
                 * and isHealthy() logic.
                 */
                public interface %s extends OutboxPublisher {

                    @Override
                    default String getName() {
                        return "%s";
                    }

                    @Override
                    default PublishMode getPreferredMode() {
                        return PublishMode.%s;
                    }
                }
                """.formatted(
                outboxPkg,
                JavaRenderSupport.GENERATED_HEADER,
                outbox.name(),
                outbox.name(),
                preferredMode
        );
    }
}
