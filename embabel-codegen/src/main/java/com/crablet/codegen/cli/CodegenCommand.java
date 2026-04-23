package com.crablet.codegen.cli;

import com.crablet.codegen.bootstrap.InitService;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.pipeline.CodegenPipeline;
import com.crablet.codegen.planning.ArtifactPlanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class CodegenCommand {

    private final CodegenPipeline pipeline;
    private final InitService initService;
    private final McpServer mcpServer;
    private final ArtifactPlanner artifactPlanner;

    public CodegenCommand(
            CodegenPipeline pipeline,
            InitService initService,
            McpServer mcpServer,
            ArtifactPlanner artifactPlanner) {
        this.pipeline = pipeline;
        this.initService = initService;
        this.mcpServer = mcpServer;
        this.artifactPlanner = artifactPlanner;
    }

    public void run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) {
            printHelp();
            return;
        }
        switch (args[0]) {
            case "plan" -> runPlan(parseFlags(args, 1));
            case "generate" -> runGenerate(parseFlags(args, 1));
            case "init" -> runInit(parseFlags(args, 1));
            case "--mcp", "mcp" -> mcpServer.run();
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                System.exit(1);
            }
        }
    }

    private void runPlan(Map<String, String> flags) throws Exception {
        String modelPath = flags.getOrDefault("--model", "event-model.yaml");
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        EventModel model = yaml.readValue(new File(modelPath), EventModel.class);
        System.out.print(artifactPlanner.render(model));
    }

    private void runGenerate(Map<String, String> flags) throws Exception {
        String modelPath = flags.getOrDefault("--model", "event-model.yaml");
        String outputPath = flags.getOrDefault("--output", ".");

        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        EventModel model = yaml.readValue(new File(modelPath), EventModel.class);
        Path outputDir = Path.of(outputPath).toAbsolutePath();

        System.out.println("Generating code from: " + modelPath);
        System.out.println("Output directory:     " + outputDir);

        pipeline.run(model, outputDir);
        System.out.println("Done. Build the application module (e.g. ./mvnw verify).");
    }

    private void runInit(Map<String, String> flags) {
        String name = flags.getOrDefault("--name", "my-service");
        String pkg = flags.getOrDefault("--package", "com.example." + name.replace("-", ""));
        String dir = flags.getOrDefault("--dir", "../" + name);
        initService.createProject(name, pkg, Path.of(dir));
        System.out.println("Project created at " + dir);
        System.out.println("Next: run the event-modeling workshop, then:");
        System.out.println("  generate --model event-model.yaml --output " + dir + "/src/main/java");
    }

    private Map<String, String> parseFlags(String[] args, int from) {
        Map<String, String> flags = new HashMap<>();
        for (int i = from; i < args.length - 1; i += 2) {
            if (args[i].startsWith("--")) {
                flags.put(args[i], args[i + 1]);
            }
        }
        return flags;
    }

    private void printHelp() {
        System.out.println("""
                Embabel Codegen — generate spring-crablet code from an event model YAML

                Commands:
                  init       Bootstrap a Spring Boot app with Crablet dependencies
                             --name my-service   artifact id
                             --package com.example.myservice
                             --dir ../my-service

                  generate   Generate code from an event model YAML
                             --model event-model.yaml   (default: event-model.yaml)
                             --output src/main/java      (default: .)

                  plan       Print planned artifacts without calling Anthropic or writing files
                             --model event-model.yaml   (default: event-model.yaml)

                  help       Show this message

                Workflow:
                  1. java -jar embabel-codegen.jar init --name my-service
                  2. Run the /event-modeling skill in Claude Code
                  3. java -jar embabel-codegen.jar plan --model event-model.yaml
                  4. java -jar embabel-codegen.jar generate --model event-model.yaml \\
                          --output ../my-service/src/main/java
                  5. cd ../my-service && ./mvnw verify
                """);
    }
}
