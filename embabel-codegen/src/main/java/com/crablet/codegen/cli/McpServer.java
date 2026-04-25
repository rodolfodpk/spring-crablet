package com.crablet.codegen.cli;

import com.crablet.codegen.bootstrap.InitService;
import com.crablet.codegen.k8s.K8sGenerator;
import com.crablet.codegen.k8s.K8sTopology;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.pipeline.CodegenPipeline;
import com.crablet.codegen.planning.ArtifactPlanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Stdio MCP server exposing embabel_init, embabel_plan, embabel_generate, and embabel_k8s as tools.
 * Activated with the --mcp flag; Claude Code discovers it via .claude/settings.json.
 *
 * Protocol: MCP 2024-11-05 over newline-delimited JSON-RPC on stdin/stdout.
 * Spring Boot logs and tool output are captured away from stdout so that only
 * protocol messages flow through.
 */
@Component
public class McpServer {

    private final CodegenPipeline pipeline;
    private final InitService initService;
    private final ArtifactPlanner artifactPlanner;
    private final K8sGenerator k8sGenerator;
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public McpServer(
            CodegenPipeline pipeline,
            InitService initService,
            ArtifactPlanner artifactPlanner,
            K8sGenerator k8sGenerator) {
        this.pipeline = pipeline;
        this.initService = initService;
        this.artifactPlanner = artifactPlanner;
        this.k8sGenerator = k8sGenerator;
    }

    public void run() throws Exception {
        PrintStream mcpOut = System.out;

        // Redirect app output so it never corrupts the protocol stream.
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        PrintStream capturePrint = new PrintStream(capture, true, StandardCharsets.UTF_8);
        System.setOut(capturePrint);
        System.setErr(capturePrint);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            capture.reset();
            try {
                String response = dispatch(line, capture);
                if (response != null) {
                    mcpOut.println(response);
                    mcpOut.flush();
                }
            } catch (Exception e) {
                try {
                    ObjectNode err = json.createObjectNode();
                    err.put("jsonrpc", "2.0");
                    err.putNull("id");
                    ObjectNode error = json.createObjectNode();
                    error.put("code", -32603);
                    error.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    err.set("error", error);
                    mcpOut.println(json.writeValueAsString(err));
                    mcpOut.flush();
                } catch (Exception ignored) {
                    // if we can't even serialize the error, silently continue
                }
            }
        }
    }

    String dispatch(String requestJson, ByteArrayOutputStream capture) throws Exception {
        JsonNode req = json.readTree(requestJson);
        JsonNode idNode = req.get("id");
        String method = req.path("method").asText();

        if (idNode == null || idNode.isNull()) return null; // notification — no response

        ObjectNode result = switch (method) {
            case "initialize" -> initializeResult();
            case "tools/list" -> toolsListResult();
            case "tools/call" -> toolCallResult(req.path("params"), capture);
            default -> null;
        };
        if (result == null) return null;

        ObjectNode response = json.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", idNode);
        response.set("result", result);
        return json.writeValueAsString(response);
    }

    private ObjectNode initializeResult() {
        ObjectNode result = json.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.set("capabilities", json.createObjectNode()
                .set("tools", json.createObjectNode()));
        ObjectNode info = json.createObjectNode();
        info.put("name", "embabel-codegen");
        info.put("version", "1.0.0");
        result.set("serverInfo", info);
        return result;
    }

    private ObjectNode toolsListResult() {
        ArrayNode tools = json.createArrayNode();

        tools.add(tool("embabel_generate",
                "Generate spring-crablet code from an event-model.yaml produced by the " +
                "/event-modeling skill. Runs the AI agent pipeline (events -> commands -> views " +
                "-> automations -> outbox) then a compile-and-fix loop.",
                schema(
                        prop("model", "string",
                                "Path to event-model.yaml (default: event-model.yaml)"),
                        prop("output", "string",
                                "Output directory for generated source files (default: .)"))));

        tools.add(tool("embabel_plan",
                "Print the planned spring-crablet artifacts for an event-model.yaml without " +
                "calling Anthropic or writing files.",
                schema(
                        prop("model", "string",
                                "Path to event-model.yaml (default: event-model.yaml)"))));

        tools.add(tool("embabel_init",
                "Bootstrap a new Spring Boot project with Crablet dependencies. " +
                "Creates pom.xml, main class, and application.yml ready for code generation.",
                schema(
                        prop("name", "string",
                                "Maven artifact id, e.g. my-service"),
                        prop("package", "string",
                                "Java base package, e.g. com.example.myservice"),
                        prop("dir", "string",
                                "Target directory path (default: ../<name>)"))));

        tools.add(tool("embabel_k8s",
                "Generate Kubernetes manifests (k8s/base) from event-model.yaml using the " +
                "deployment topology and module list. No Anthropic calls.",
                schema(
                        prop("model", "string",
                                "Path to event-model.yaml (default: event-model.yaml)"),
                        prop("output", "string",
                                "Output directory; k8s/base is created under it (default: .)"))));

        ObjectNode result = json.createObjectNode();
        result.set("tools", tools);
        return result;
    }

    private ObjectNode toolCallResult(JsonNode params, ByteArrayOutputStream capture) {
        String toolName = params.path("name").asText();
        JsonNode args = params.path("arguments");
        boolean isError = false;
        String output;
        try {
            output = switch (toolName) {
                case "embabel_generate" -> callGenerate(args, capture);
                case "embabel_plan" -> callPlan(args);
                case "embabel_init" -> callInit(args, capture);
                case "embabel_k8s" -> callK8s(args);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
            String captured = capture.toString(StandardCharsets.UTF_8);
            if (!captured.isBlank()) output += "\n" + captured;
            isError = true;
        }

        ObjectNode result = json.createObjectNode();
        ArrayNode content = json.createArrayNode();
        ObjectNode text = json.createObjectNode();
        text.put("type", "text");
        text.put("text", output);
        content.add(text);
        result.set("content", content);
        if (isError) result.put("isError", true);
        return result;
    }

    private String callGenerate(JsonNode args, ByteArrayOutputStream capture) throws Exception {
        String modelPath = args.path("model").asText("event-model.yaml");
        String outputPath = args.path("output").asText(".");
        EventModel model = yaml.readValue(new File(modelPath), EventModel.class);
        Path outputDir = Path.of(outputPath).toAbsolutePath();
        pipeline.run(model, outputDir);
        String log = capture.toString(StandardCharsets.UTF_8).trim();
        return "Generated code from " + modelPath + " into " + outputDir
                + (log.isBlank() ? "" : "\n\n" + log);
    }

    private String callPlan(JsonNode args) throws Exception {
        String modelPath = args.path("model").asText("event-model.yaml");
        EventModel model = yaml.readValue(new File(modelPath), EventModel.class);
        return artifactPlanner.render(model);
    }

    private String callK8s(JsonNode args) throws Exception {
        String modelPath = args.path("model").asText("event-model.yaml");
        String outputPath = args.path("output").asText(".");
        EventModel model = yaml.readValue(new File(modelPath), EventModel.class);
        Path outputDir = Path.of(outputPath).toAbsolutePath();
        k8sGenerator.generate(K8sTopology.from(model), outputDir);
        return "Wrote k8s manifests to " + outputDir.resolve("k8s/base");
    }

    private String callInit(JsonNode args, ByteArrayOutputStream capture) {
        String name = args.path("name").asText("my-service");
        String pkg = args.path("package").asText("com.example." + name.replace("-", ""));
        String dir = args.path("dir").asText("../" + name);
        initService.createProject(name, pkg, Path.of(dir));
        String log = capture.toString(StandardCharsets.UTF_8).trim();
        return "Initialized project at " + dir
                + (log.isBlank() ? "" : "\n\n" + log)
                + "\n\nNext: run /event-modeling then embabel_generate.";
    }

    // --- builders ---

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        ObjectNode t = json.createObjectNode();
        t.put("name", name);
        t.put("description", description);
        t.set("inputSchema", inputSchema);
        return t;
    }

    private ObjectNode schema(ObjectNode... properties) {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = json.createObjectNode();
        for (ObjectNode p : properties) props.setAll(p);
        schema.set("properties", props);
        return schema;
    }

    private ObjectNode prop(String name, String type, String description) {
        ObjectNode wrapper = json.createObjectNode();
        ObjectNode p = json.createObjectNode();
        p.put("type", type);
        p.put("description", description);
        wrapper.set(name, p);
        return wrapper;
    }
}
