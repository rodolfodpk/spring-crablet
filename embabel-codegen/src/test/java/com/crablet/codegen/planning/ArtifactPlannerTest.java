package com.crablet.codegen.planning;

import com.crablet.codegen.model.AutomationSpec;
import com.crablet.codegen.model.EventModel;
import com.crablet.codegen.model.OutboxSpec;
import com.crablet.codegen.model.ViewSpec;
import com.crablet.codegen.pipeline.SchemaResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactPlannerTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final ArtifactPlanner planner = new ArtifactPlanner(new SchemaResolver());

    @Test
    void plansDocumentedLoanFeatureSliceWithoutGeneration() throws Exception {
        EventModel model = yaml.readValue(
                Path.of("..", "docs", "examples", "loan-submit-feature-slice-event-model.yaml").toFile(),
                EventModel.class
        );

        String plan = planner.render(model);

        assertThat(plan).contains("Planned artifacts for LoanApplication (com.example.loan)");
        assertThat(plan).contains("com.example.loan.domain.LoanApplicationEvent");
        assertThat(plan).contains("com.example.loan.domain.LoanApplicationSubmitted");
        assertThat(plan).contains("com.example.loan.command.SubmitLoanApplication");
        assertThat(plan).contains("com.example.loan.command.SubmitLoanApplicationCommandHandler");
        assertThat(plan).contains("com.example.loan.command.SubmitLoanApplicationCommandHandler (Java interface)");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationState");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationState (Java class)");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationStateProjector");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationStateProjector (Java class)");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationQueryPatterns");
        assertThat(plan).contains("com.example.loan.command.LoanApplicationQueryPatterns (Java class)");
        assertThat(plan).contains("com.example.loan.view.PendingLoanApplicationsViewProjector");
        assertThat(plan).contains("V100__create_pending_loan_applications.sql");
        assertThat(plan).doesNotContain("AutomationHandler");
        assertThat(plan).doesNotContain("Outbox:");
    }

    @Test
    void plansAutomationAndOutboxAsInterfacesWhileViewsStayClasses() {
        EventModel model = new EventModel(
                "LoanApplication",
                "com.example.loan",
                null,
                null,
                null,
                List.of(new ViewSpec("PendingLoanApplications", List.of("LoanApplicationSubmitted"), "application_id", List.of())),
                List.of(new AutomationSpec(
                        "welcome-email",
                        "LoanApplicationSubmitted",
                        "SendWelcomeEmail",
                        null,
                        null,
                        null
                )),
                List.of(new OutboxSpec(
                        "LoanNotificationsPublisher",
                        "loan-notifications",
                        List.of("LoanApplicationSubmitted"),
                        "http"
                )),
                null
        );

        String plan = planner.render(model);

        assertThat(plan).contains("com.example.loan.automation.WelcomeEmailAutomationHandler (Java interface)");
        assertThat(plan).contains("com.example.loan.outbox.LoanNotificationsPublisher (Java interface)");
        assertThat(plan).contains("com.example.loan.view.PendingLoanApplicationsViewProjector (Java class)");
    }
}
