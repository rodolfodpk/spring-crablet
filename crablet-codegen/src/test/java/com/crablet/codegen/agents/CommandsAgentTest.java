package com.crablet.codegen.agents;

import com.crablet.codegen.llm.CodegenLlmClient;
import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.tools.FileWriterTool;
import com.crablet.codegen.tools.TemplateLoader;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommandsAgentTest {

    @TempDir
    Path tempDir;

    @Test
    void promptIncludesEventTagsForDcbDecisionGeneration() {
        CapturingLlmClient llm = new CapturingLlmClient();
        CommandsAgent agent = new CommandsAgent(
                llm,
                new FileWriterTool(),
                new TemplateLoader("CLAUDE.md"));

        EventModel model = new EventModel(
                "LoanApplication",
                "com.example.loan",
                null,
                List.of(new EventSpec(
                        "LoanApplicationSubmitted",
                        List.of("application_id", "customer_id"),
                        null,
                        List.of(
                                new FieldSpec("applicationId", "string", null, null, null, null, null, null, null, null, null, null),
                                new FieldSpec("customerId", "string", null, null, null, null, null, null, null, null, null, null)))),
                List.of(new CommandSpec(
                        "SubmitLoanApplication",
                        "idempotent",
                        List.of("LoanApplicationSubmitted"),
                        List.of(),
                        null,
                        List.of(new FieldSpec("applicationId", "string", null, null, null, null, 1, null, null, null, null, null)),
                        Map.of())),
                null,
                null,
                null,
                null,
                null,
                null);

        agent.generate(model, tempDir);

        assertThat(llm.userPrompt)
                .contains("Events (already generated in package com.example.loan.domain; tags are event-store consistency/query keys):")
                .contains("LoanApplicationSubmitted(tags=[application_id, customer_id]")
                .contains("fields=applicationId:string, customerId:string")
                .contains("SubmitLoanApplication [pattern=idempotent");
    }

    private static class CapturingLlmClient implements CodegenLlmClient {
        private String userPrompt = "";

        @Override
        public @NonNull String complete(@NonNull String systemPrompt, @NonNull String userPrompt) {
            this.userPrompt = userPrompt;
            return "";
        }
    }
}
