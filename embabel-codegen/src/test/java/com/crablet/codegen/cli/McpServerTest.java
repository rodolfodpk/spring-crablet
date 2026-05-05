package com.crablet.codegen.cli;

import com.crablet.codegen.k8s.K8sGenerator;
import com.crablet.codegen.gherkin.GherkinImportService;
import com.crablet.codegen.pipeline.SchemaResolver;
import com.crablet.codegen.planning.ArtifactPlanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final McpServer server = new McpServer(
            null,
            null,
            new GherkinImportService(),
            new ArtifactPlanner(new SchemaResolver()),
            new K8sGenerator()
    );

    @Test
    void toolsListIncludesPlanTool() throws Exception {
        String response = server.dispatch("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                """, new ByteArrayOutputStream());

        JsonNode root = json.readTree(response);
        JsonNode tools = root.path("result").path("tools");

        assertThat(tools).hasSize(5);
        assertThat(tools.findValuesAsText("name"))
                .contains("embabel_init", "embabel_plan", "embabel_generate", "embabel_k8s", "embabel_import_gherkin");
    }

    @Test
    void planToolReturnsArtifactsWithoutGeneration() throws Exception {
        String response = server.dispatch("""
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "embabel_plan",
                    "arguments": {
                      "model": "../docs/user/examples/loan-submit-feature-slice-event-model.yaml"
                    }
                  }
                }
                """, new ByteArrayOutputStream());

        JsonNode root = json.readTree(response);
        String text = root.path("result").path("content").get(0).path("text").asText();

        assertThat(root.path("result").path("isError").asBoolean(false)).isFalse();
        assertThat(text).contains("Planned artifacts for LoanApplication (com.example.loan)");
        assertThat(text).contains("com.example.loan.command.SubmitLoanApplicationCommandHandler");
        assertThat(text).contains("V100__create_pending_loan_applications.sql");
    }

    @Test
    void importGherkinToolWritesDraftEventModel() throws Exception {
        String response = server.dispatch("""
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "embabel_import_gherkin",
                    "arguments": {
                      "input": "../docs/user/examples/submit-loan-application.feature",
                      "output": "../target/imported-event-model.yaml",
                      "domain": "LoanApplication",
                      "base-package": "com.example.loan"
                    }
                  }
                }
                """, new ByteArrayOutputStream());

        JsonNode root = json.readTree(response);
        String text = root.path("result").path("content").get(0).path("text").asText();

        assertThat(root.path("result").path("isError").asBoolean(false)).isFalse();
        assertThat(text).contains("Imported Gherkin from");
        assertThat(text).contains("draft event-model.yaml");
    }
}
