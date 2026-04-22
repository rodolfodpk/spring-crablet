package com.crablet.codegen.pipeline;

import com.crablet.codegen.agents.*;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.tools.MavenTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class CodegenPipeline {

    private final SchemaResolver schemaResolver;
    private final EventsAgent eventsAgent;
    private final CommandsAgent commandsAgent;
    private final ViewsAgent viewsAgent;
    private final AutomationsAgent automationsAgent;
    private final OutboxAgent outboxAgent;
    private final RepairAgent repairAgent;
    private final MavenTool maven;

    public CodegenPipeline(
            SchemaResolver schemaResolver,
            EventsAgent eventsAgent,
            CommandsAgent commandsAgent,
            ViewsAgent viewsAgent,
            AutomationsAgent automationsAgent,
            OutboxAgent outboxAgent,
            RepairAgent repairAgent,
            MavenTool maven) {
        this.schemaResolver = schemaResolver;
        this.eventsAgent = eventsAgent;
        this.commandsAgent = commandsAgent;
        this.viewsAgent = viewsAgent;
        this.automationsAgent = automationsAgent;
        this.outboxAgent = outboxAgent;
        this.repairAgent = repairAgent;
        this.maven = maven;
    }

    public void run(EventModel model, Path outputDir) {
        EventModel resolved = schemaResolver.resolve(model);

        eventsAgent.generate(resolved, outputDir);
        commandsAgent.generate(resolved, outputDir);
        viewsAgent.generate(resolved, outputDir);
        automationsAgent.generate(resolved, outputDir);
        outboxAgent.generate(resolved, outputDir);

        for (int attempt = 1; attempt <= 3; attempt++) {
            CompileResult result = maven.compile(outputDir);
            if (result.success()) {
                System.out.println("Compilation succeeded on attempt " + attempt + ".");
                return;
            }
            System.out.printf("Attempt %d: %d error(s) — repairing...%n",
                    attempt, result.errors().size());
            result.errors().forEach(e ->
                    System.out.println("  " + e.file().getFileName() + ":" + e.line() + " " + e.message()));
            repairAgent.fix(result.errors(), outputDir);
        }

        System.err.println("[WARN] Compilation still failing after 3 repair attempts. Review errors above.");
    }
}
