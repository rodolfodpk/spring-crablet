package com.crablet.codegen.gherkin;

import com.crablet.codegen.model.EventModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GherkinImportServiceTest {

    private final GherkinImportService service = new GherkinImportService();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void importsFeatureIntoDraftEventModel(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("loan-submit.feature");
        Files.writeString(feature, """
                Feature: Submit Loan Application

                  @loan @vertical-slice
                  Scenario: Submit a new loan application
                    Given a customer submits a loan application
                    When the request is accepted
                    Then the system records LoanApplicationSubmitted
                """);

        EventModel model = service.importFeature(feature, "LoanApplication", "com.example.loan");

        assertThat(model.domain()).isEqualTo("LoanApplication");
        assertThat(model.basePackage()).isEqualTo("com.example.loan");
        assertThat(model.scenarios()).hasSize(1);
        assertThat(model.scenarios().get(0).name()).isEqualTo("Submit a new loan application");
        assertThat(model.scenarios().get(0).tags()).containsExactly("loan", "vertical-slice");
        assertThat(model.scenarios().get(0).steps()).hasSize(3);
        assertThat(model.scenarios().get(0).steps().get(0).keyword()).isEqualTo("Given");
        assertThat(model.scenarios().get(0).steps().get(2).text()).isEqualTo("the system records LoanApplicationSubmitted");
    }

    @Test
    void writesDraftYaml(@TempDir Path tempDir) throws Exception {
        Path feature = tempDir.resolve("workflow.feature");
        Files.writeString(feature, """
                Feature: Example

                  Scenario: One thing
                    Given something happens
                    Then it is recorded
                """);

        Path output = tempDir.resolve("event-model.yaml");
        service.writeImportedModel(feature, output, null, null);

        EventModel model = yaml.readValue(Files.readString(output), EventModel.class);
        assertThat(model.domain()).isEqualTo("Example");
        assertThat(model.scenarios()).hasSize(1);
        assertThat(model.scenarios().get(0).steps()).hasSize(2);
    }
}
