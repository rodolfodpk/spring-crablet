package com.crablet.codegen.pipeline;

import com.crablet.codegen.generator.AutomationsGenerator;
import com.crablet.codegen.generator.CommandsGenerator;
import com.crablet.codegen.generator.EventsGenerator;
import com.crablet.codegen.generator.OutboxGenerator;
import com.crablet.codegen.generator.ViewsGenerator;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.planning.ModelValidator;
import com.crablet.codegen.scaffold.ScenarioScaffoldGenerator;
import com.crablet.codegen.tools.MavenTool;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class CodegenPipeline {

    private final SchemaResolver schemaResolver;
    private final ModelValidator modelValidator;
    private final EventsGenerator eventsGenerator;
    private final CommandsGenerator commandsGenerator;
    private final ViewsGenerator viewsGenerator;
    private final AutomationsGenerator automationsGenerator;
    private final OutboxGenerator outboxGenerator;
    private final ScenarioScaffoldGenerator scenarioScaffoldGenerator;
    private final MavenTool maven;

    public CodegenPipeline(
            SchemaResolver schemaResolver,
            ModelValidator modelValidator,
            EventsGenerator eventsGenerator,
            CommandsGenerator commandsGenerator,
            ViewsGenerator viewsGenerator,
            AutomationsGenerator automationsGenerator,
            OutboxGenerator outboxGenerator,
            ScenarioScaffoldGenerator scenarioScaffoldGenerator,
            MavenTool maven) {
        this.schemaResolver = schemaResolver;
        this.modelValidator = modelValidator;
        this.eventsGenerator = eventsGenerator;
        this.commandsGenerator = commandsGenerator;
        this.viewsGenerator = viewsGenerator;
        this.automationsGenerator = automationsGenerator;
        this.outboxGenerator = outboxGenerator;
        this.scenarioScaffoldGenerator = scenarioScaffoldGenerator;
        this.maven = maven;
    }

    public boolean run(EventModel model, Path outputDir) {
        EventModel resolved = schemaResolver.resolve(model);
        modelValidator.validate(resolved);

        eventsGenerator.generate(resolved, outputDir);
        commandsGenerator.generate(resolved, outputDir);
        viewsGenerator.generate(resolved, outputDir);
        automationsGenerator.generate(resolved, outputDir);
        outboxGenerator.generate(resolved, outputDir);
        scenarioScaffoldGenerator.generate(resolved, outputDir);

        CompileResult result = maven.compile(outputDir);
        if (result.success()) {
            System.out.println("Compilation succeeded.");
            return true;
        }

        System.err.printf("[WARN] Compilation failed with %d error(s). Review errors below.%n",
                result.errors().size());
        result.errors().forEach(e ->
                System.err.println("  " + e.file().getFileName() + ":" + e.line() + " " + e.message()));
        return false;
    }
}
