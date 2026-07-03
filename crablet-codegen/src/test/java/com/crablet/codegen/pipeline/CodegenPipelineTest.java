package com.crablet.codegen.pipeline;

import com.crablet.codegen.generator.AutomationsGenerator;
import com.crablet.codegen.generator.CommandsGenerator;
import com.crablet.codegen.generator.EventsGenerator;
import com.crablet.codegen.generator.OutboxGenerator;
import com.crablet.codegen.generator.ViewsGenerator;
import com.crablet.codegen.model.CommandSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.EventSpec;
import com.crablet.codegen.model.FieldSpec;
import com.crablet.codegen.planning.ModelValidationException;
import com.crablet.codegen.planning.ModelValidator;
import com.crablet.codegen.scaffold.ScenarioScaffoldGenerator;
import com.crablet.codegen.tools.FileWriterTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodegenPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void generateRejectsInvalidModelBeforeWritingFiles() {
        FileWriterTool writer = new FileWriterTool();
        CodegenPipeline pipeline = new CodegenPipeline(
                new SchemaResolver(),
                new ModelValidator(),
                new EventsGenerator(writer),
                new CommandsGenerator(writer),
                new ViewsGenerator(writer),
                new AutomationsGenerator(writer),
                new OutboxGenerator(writer),
                new ScenarioScaffoldGenerator(),
                null /* MavenTool — never reached when validation fails */);

        EventModel bad = new EventModel(
                "Loan",
                "com.example.loan",
                null,
                List.of(new EventSpec(
                        "LoanApplicationSubmitted",
                        List.of("application_id"),
                        null,
                        List.of(new FieldSpec("applicationId", "string", null, null, null, null, null, null, null, null, null, null)))),
                List.of(new CommandSpec(
                        "SubmitLoanApplication",
                        "idempotent",
                        List.of("NonExistentEvent"),
                        List.of(),
                        null,
                        List.of(new FieldSpec("applicationId", "string", null, null, null, null, 1, null, null, null, null, null)),
                        Map.of())),
                null, null, null, null, null, null);

        assertThatThrownBy(() -> pipeline.run(bad, tempDir))
                .isInstanceOf(ModelValidationException.class)
                .hasMessageContaining("produces unknown event 'NonExistentEvent'");

        assertThat(tempDir).isEmptyDirectory();
    }
}
