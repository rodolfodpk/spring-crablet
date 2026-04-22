package com.crablet.codegen.model;

import com.crablet.codegen.pipeline.SchemaResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EventModelParsingTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void parseWalletEventModel() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("wallet-event-model.yaml")) {
            assertThat(in).isNotNull();
            EventModel model = yaml.readValue(in, EventModel.class);

            assertThat(model.domain()).isEqualTo("Wallet");
            assertThat(model.basePackage()).isEqualTo("com.example.wallet");

            // schemas
            assertThat(model.schemas()).hasSize(1);
            assertThat(model.schemas().get(0).name()).isEqualTo("MoneyAmount");

            // events
            assertThat(model.events()).hasSize(4);
            assertThat(model.eventNames())
                    .containsExactly("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");

            // scalar validation (greaterThan(0)) parsed as single-element list
            FieldSpec amountField = model.schemas().get(0).fields().stream()
                    .filter(f -> f.name().equals("amount")).findFirst().orElseThrow();
            assertThat(amountField.validation()).containsExactly("greaterThan(0)");

            // list validation ([notNull, notBlank]) parsed as list
            FieldSpec descField = model.schemas().get(0).fields().stream()
                    .filter(f -> f.name().equals("description")).findFirst().orElseThrow();
            assertThat(descField.validation()).containsExactly("notNull", "notBlank");

            // commands
            assertThat(model.commands()).hasSize(4);
            CommandSpec open = model.commands().get(0);
            assertThat(open.name()).isEqualTo("OpenWallet");
            assertThat(open.isIdempotent()).isTrue();
            assertThat(open.produces()).containsExactly("WalletOpened");

            CommandSpec deposit = model.commands().get(1);
            assertThat(deposit.isCommutative()).isTrue();
            assertThat(deposit.guardEvents()).containsExactly("WalletOpened");

            CommandSpec withdraw = model.commands().get(2);
            assertThat(withdraw.isNonCommutative()).isTrue();

            // views
            assertThat(model.views()).hasSize(1);
            ViewSpec balance = model.views().get(0);
            assertThat(balance.name()).isEqualTo("WalletBalance");
            assertThat(balance.tableName()).isEqualTo("wallet_balance");
            assertThat(balance.reads()).containsExactlyInAnyOrder(
                    "WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");

            // automations
            assertThat(model.automations()).hasSize(1);
            AutomationSpec auto = model.automations().get(0);
            assertThat(auto.name()).isEqualTo("welcome-notification");
            assertThat(auto.triggeredBy()).isEqualTo("WalletOpened");
            assertThat(auto.emitsCommand()).isEqualTo("SendWelcomeNotification");
        }
    }

    @Test
    void schemaResolverInlinesSchemaRefs() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("wallet-event-model.yaml")) {
            EventModel model = yaml.readValue(in, EventModel.class);
            SchemaResolver resolver = new SchemaResolver();
            EventModel resolved = resolver.resolve(model);

            // Deposit command has schema:MoneyAmount — should be inlined into its fields
            CommandSpec deposit = resolved.commands().stream()
                    .filter(c -> c.name().equals("Deposit"))
                    .findFirst().orElseThrow();

            boolean hasAmount = deposit.fields().stream()
                    .anyMatch(f -> "amount".equals(f.name()) && "int".equals(f.type()));
            boolean hasDescription = deposit.fields().stream()
                    .anyMatch(f -> "description".equals(f.name()));
            assertThat(hasAmount).isTrue();
            assertThat(hasDescription).isTrue();
        }
    }

    @Test
    void parseDocumentedLoanFeatureSliceModel() throws Exception {
        Path docExample = Path.of("..", "docs", "examples", "loan-submit-feature-slice-event-model.yaml");
        assertThat(Files.exists(docExample)).isTrue();

        EventModel model = yaml.readValue(docExample.toFile(), EventModel.class);

        assertThat(model.domain()).isEqualTo("LoanApplication");
        assertThat(model.basePackage()).isEqualTo("com.example.loan");
        assertThat(model.eventNames()).containsExactly("LoanApplicationSubmitted");

        CommandSpec submit = model.commands().get(0);
        assertThat(submit.name()).isEqualTo("SubmitLoanApplication");
        assertThat(submit.isIdempotent()).isTrue();
        assertThat(submit.produces()).containsExactly("LoanApplicationSubmitted");

        ViewSpec pending = model.views().get(0);
        assertThat(pending.name()).isEqualTo("PendingLoanApplications");
        assertThat(pending.tableName()).isEqualTo("pending_loan_applications");
        assertThat(pending.reads()).containsExactly("LoanApplicationSubmitted");
    }
}
