package com.crablet.codegen.agents;

import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.ScenarioSpec;
import com.crablet.codegen.model.ScenarioStepSpec;
import com.crablet.codegen.tools.TemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenariosAgentTest {

    private final ScenariosAgent agent = new ScenariosAgent(new TemplateLoader("CLAUDE.md"));

    @TempDir
    Path tempDir;

    private Path mainJavaDir() throws IOException {
        Path dir = tempDir.resolve("src/main/java");
        Files.createDirectories(dir);
        return dir;
    }

    private EventModel modelWithScenario(String name, List<ScenarioStepSpec> steps) {
        return new EventModel(
                "Loan", "com.example.loan",
                null, null, null, null, null, null,
                List.of(new ScenarioSpec(name, null, steps)),
                null, null
        );
    }

    @Test
    void skipsWhenScenariosEmpty() throws Exception {
        EventModel model = new EventModel(
                "Loan", "com.example.loan",
                null, null, null, null, null, null, List.of(), null, null
        );
        agent.generate(model, mainJavaDir());
        assertThat(Files.walk(tempDir).filter(Files::isRegularFile)).isEmpty();
    }

    @Test
    void writesTestFileForScenario() throws Exception {
        List<ScenarioStepSpec> steps = List.of(
                new ScenarioStepSpec("Given", "a loan application does not exist"),
                new ScenarioStepSpec("When", "submit loan application for applicant APPLICANT_001"),
                new ScenarioStepSpec("Then", "the system records LoanApplicationSubmitted")
        );
        EventModel model = modelWithScenario("Submit loan application", steps);

        agent.generate(model, mainJavaDir());

        Path expected = tempDir.resolve(
                "src/test/java/com/example/loan/test/SubmitLoanApplicationScenarioTest.java");
        assertThat(expected).exists();
        String content = Files.readString(expected);
        assertThat(content).contains("class SubmitLoanApplicationScenarioTest");
        assertThat(content).contains("@Test");
        assertThat(content).contains("@DisplayName(\"Submit loan application\")");
        assertThat(content).contains("void submitLoanApplication()");
        assertThat(content).contains("// Given: a loan application does not exist");
        assertThat(content).contains("// When: submit loan application for applicant APPLICANT_001");
        assertThat(content).contains("// Then: the system records LoanApplicationSubmitted");
    }

    @Test
    void skipsExistingFile() throws Exception {
        Path target = tempDir.resolve(
                "src/test/java/com/example/loan/test/SubmitLoanApplicationScenarioTest.java");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "sentinel content");

        EventModel model = modelWithScenario("Submit loan application", List.of());
        agent.generate(model, mainJavaDir());

        assertThat(Files.readString(target)).isEqualTo("sentinel content");
    }

    @Test
    void andAndButRenderWithOwnKeyword() throws Exception {
        List<ScenarioStepSpec> steps = List.of(
                new ScenarioStepSpec("Given", "an account exists"),
                new ScenarioStepSpec("And", "the account is active"),
                new ScenarioStepSpec("When", "a deposit is made"),
                new ScenarioStepSpec("Then", "balance increases"),
                new ScenarioStepSpec("But", "overdraft flag is not set")
        );
        EventModel model = modelWithScenario("Deposit funds", steps);

        agent.generate(model, mainJavaDir());

        Path generated = tempDir.resolve(
                "src/test/java/com/example/loan/test/DepositFundsScenarioTest.java");
        String content = Files.readString(generated);
        assertThat(content).contains("// And: the account is active");
        assertThat(content).contains("// But: overdraft flag is not set");
        assertThat(content).doesNotContain("// Given: the account is active");
        assertThat(content).doesNotContain("// Given: overdraft flag is not set");
    }

    @Test
    void derivesTestPathFromMainJava() throws Exception {
        Path mainJava = mainJavaDir();
        EventModel model = modelWithScenario("My scenario", List.of());

        agent.generate(model, mainJava);

        Path generated = tempDir.resolve(
                "src/test/java/com/example/loan/test/MyScenarioScenarioTest.java");
        assertThat(generated).exists();
    }

    @Test
    void failsWithClearMessageWhenNoMainJavaSegment() throws IOException {
        Path noMainJava = tempDir.resolve("output");
        Files.createDirectories(noMainJava);
        EventModel model = modelWithScenario("Any scenario", List.of());

        assertThatThrownBy(() -> agent.generate(model, noMainJava))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("src/main/java");
    }

    @Test
    void escapesDisplayNameQuotesAndBackslashes() throws Exception {
        EventModel model = modelWithScenario("Say \"hello\" and \\bye", List.of());

        agent.generate(model, mainJavaDir());

        Path generated = tempDir.resolve(
                "src/test/java/com/example/loan/test/SayHelloAndByeScenarioTest.java");
        String content = Files.readString(generated);
        assertThat(content).contains("@DisplayName(\"Say \\\"hello\\\" and \\\\bye\")");
    }

    @Test
    void collapsesNewlinesInStepText() throws Exception {
        List<ScenarioStepSpec> steps = List.of(
                new ScenarioStepSpec("Given", "line one\nline two")
        );
        EventModel model = modelWithScenario("Multiline step", steps);

        agent.generate(model, mainJavaDir());

        Path generated = tempDir.resolve(
                "src/test/java/com/example/loan/test/MultilineStepScenarioTest.java");
        String content = Files.readString(generated);
        assertThat(content).contains("// Given: line one line two");
        assertThat(content).doesNotContain("// Given: line one\nline two");
    }
}
