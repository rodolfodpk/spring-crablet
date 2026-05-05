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

            // JSON Schema constraints on MoneyAmount.amount
            FieldSpec amountField = model.schemas().get(0).fields().stream()
                    .filter(f -> f.name().equals("amount")).findFirst().orElseThrow();
            assertThat(amountField.type()).isEqualTo("integer");
            assertThat(amountField.exclusiveMinimum()).isEqualTo(0);
            assertThat(amountField.yaviMethod()).isEqualTo("_integer");
            assertThat(amountField.yaviConstraints()).isEqualTo(".greaterThan(0)");

            // JSON Schema constraints on MoneyAmount.description
            FieldSpec descField = model.schemas().get(0).fields().stream()
                    .filter(f -> f.name().equals("description")).findFirst().orElseThrow();
            assertThat(descField.type()).isEqualTo("string");
            assertThat(descField.minLength()).isEqualTo(1);
            assertThat(descField.yaviConstraints()).isEqualTo(".notBlank()");

            // events
            assertThat(model.events()).hasSize(4);
            assertThat(model.eventNames())
                    .containsExactly("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");

            // event fields have no constraints (events are facts)
            FieldSpec walletIdField = model.events().get(0).fields().stream()
                    .filter(f -> f.name().equals("walletId")).findFirst().orElseThrow();
            assertThat(walletIdField.type()).isEqualTo("string");
            assertThat(walletIdField.hasConstraints()).isFalse();

            // commands
            assertThat(model.commands()).hasSize(4);
            CommandSpec open = model.commands().get(0);
            assertThat(open.name()).isEqualTo("OpenWallet");
            assertThat(open.isIdempotent()).isTrue();
            assertThat(open.produces()).containsExactly("WalletOpened");

            // OpenWallet.initialBalance: minimum: 0
            FieldSpec initialBalance = open.fields().stream()
                    .filter(f -> f.name().equals("initialBalance")).findFirst().orElseThrow();
            assertThat(initialBalance.minimum()).isEqualTo(0);
            assertThat(initialBalance.yaviConstraints()).isEqualTo(".greaterThanOrEqual(0)");

            CommandSpec deposit = model.commands().get(1);
            assertThat(deposit.isCommutative()).isTrue();
            assertThat(deposit.guardEvents()).containsExactly("WalletOpened");

            CommandSpec withdraw = model.commands().get(2);
            assertThat(withdraw.isNonCommutative()).isTrue();
            assertThat(withdraw.guardEvents()).containsExactly("WalletOpened");

            CommandSpec transfer = model.commands().get(3);
            assertThat(transfer.isNonCommutative()).isTrue();
            assertThat(transfer.guardEvents()).containsExactly("WalletOpened");

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

            // amount and description come from MoneyAmount schema
            FieldSpec amount = deposit.fields().stream()
                    .filter(f -> "amount".equals(f.name())).findFirst().orElseThrow();
            assertThat(amount.type()).isEqualTo("integer");
            assertThat(amount.exclusiveMinimum()).isEqualTo(0);

            FieldSpec description = deposit.fields().stream()
                    .filter(f -> "description".equals(f.name())).findFirst().orElseThrow();
            assertThat(description.minLength()).isEqualTo(1);
        }
    }

    @Test
    void javaTypeResolvesJsonSchemaNames() {
        assertThat(scalar("string").javaType()).isEqualTo("String");
        assertThat(scalar("integer").javaType()).isEqualTo("int");
        assertThat(scalar("boolean").javaType()).isEqualTo("boolean");
        assertThat(scalar("number").javaType()).isEqualTo("java.math.BigDecimal");
        assertThat(scalar("UUID").javaType()).isEqualTo("java.util.UUID");
        assertThat(scalar("Instant").javaType()).isEqualTo("java.time.Instant");
    }

    @Test
    void collectionTypesResolveCorrectly() {
        FieldSpec stringItems  = scalar("string");
        FieldSpec intItems     = scalar("integer");
        FieldSpec uuidItems    = scalar("UUID");
        FieldSpec stringValues = scalar("string");

        FieldSpec arrayOfString  = new FieldSpec("x", "array", null, null, null, null, null, null, stringItems, null, null, null);
        FieldSpec arrayOfInteger = new FieldSpec("x", "array", null, null, null, null, null, null, intItems, null, null, null);
        FieldSpec arrayOfUUID    = new FieldSpec("x", "array", null, null, null, null, null, null, uuidItems, null, null, null);
        FieldSpec mapOfString    = new FieldSpec("x", "map",   null, null, null, null, null, null, null, stringValues, null, null);

        assertThat(arrayOfString.javaType()).isEqualTo("List<String>");
        assertThat(arrayOfInteger.javaType()).isEqualTo("List<Integer>");
        assertThat(arrayOfUUID.javaType()).isEqualTo("List<java.util.UUID>");
        assertThat(mapOfString.javaType()).isEqualTo("Map<String, String>");

        assertThat(arrayOfString.displayType()).isEqualTo("array<string>");
        assertThat(mapOfString.displayType()).isEqualTo("map<string,string>");

        FieldSpec arrayWithMinItems = new FieldSpec("x", "array", null, null, null, null, null, null, stringItems, null, 1, null);
        assertThat(arrayWithMinItems.yaviConstraints()).isEqualTo(".greaterThanOrEqualTo(1)");
        assertThat(arrayWithMinItems.hasConstraints()).isTrue();
    }

    @Test
    void diagramAbsentBecomesEmptyNotNull() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                """, EventModel.class);

        assertThat(model.diagram()).isNotNull();
        assertThat(model.diagram().lanes()).isEmpty();
        assertThat(model.diagram().assignments()).isEmpty();
        assertThat(model.diagram().triggers()).isEmpty();
        assertThat(model.diagram().syntheticCommands()).isEmpty();
        assertThat(model.diagram().eventBadges()).isEmpty();
        assertThat(model.diagram().automations()).isEmpty();
        assertThat(model.diagram().actors()).isEmpty();
    }

    @Test
    void scenariosParseAndDefaultToEmptyWhenAbsent() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                scenarios:
                  - name: Submit a new loan application
                    tags: [loan]
                    steps:
                      - keyword: Given
                        text: a customer submits a loan application
                      - keyword: Then
                        text: the system records LoanApplicationSubmitted
                """, EventModel.class);

        assertThat(model.scenarios()).hasSize(1);
        assertThat(model.scenarios().get(0).name()).isEqualTo("Submit a new loan application");
        assertThat(model.scenarios().get(0).steps()).hasSize(2);
    }

    @Test
    void diagramLanesAndAssignmentsParse() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("wallet-event-model.yaml")) {
            EventModel model = yaml.readValue(in, EventModel.class);

            DiagramSpec diagram = model.diagram();
            assertThat(diagram.lanes()).hasSize(2);
            assertThat(diagram.lanes().get(0).id()).isEqualTo("wallet");
            assertThat(diagram.lanes().get(0).label()).isEqualTo("Wallet");
            assertThat(diagram.lanes().get(1).id()).isEqualTo("notification");

            assertThat(diagram.actors()).hasSize(1);
            assertThat(diagram.actors().get(0).id()).isEqualTo("customer");
            assertThat(diagram.assignments()).containsEntry("WalletBalance", "wallet");
            assertThat(diagram.assignments()).containsEntry("SendWelcomeNotification", "notification");
            assertThat(diagram.assignments()).doesNotContainKey("OpenWallet");
        }
    }

    @Test
    void diagramFullV1OverlaySetParses() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("wallet-event-model.yaml")) {
            EventModel model = yaml.readValue(in, EventModel.class);
            DiagramSpec diagram = model.diagram();

            assertThat(diagram.triggers()).hasSize(1);
            assertThat(diagram.triggers().get(0).name()).isEqualTo("Customer opens wallet");
            assertThat(diagram.triggers().get(0).linkedCommand()).isEqualTo("OpenWallet");
            assertThat(diagram.triggers().get(0).actor()).isEqualTo("customer");

            assertThat(diagram.syntheticCommands()).hasSize(1);
            assertThat(diagram.syntheticCommands().get(0).name()).isEqualTo("SendWelcomeNotification");
            assertThat(diagram.syntheticCommands().get(0).displayLabel()).isEqualTo("SendWelcomeNotification");
            assertThat(diagram.syntheticCommands().get(0).note()).isEqualTo("notification subdomain");

            assertThat(diagram.eventBadges()).containsEntry("WalletOpened", "lifecycle");

            assertThat(diagram.automations()).hasSize(1);
            assertThat(diagram.automations().get(0).name()).isEqualTo("WalletOpenedAutomation");
            assertThat(diagram.automations().get(0).triggeredBy()).isEqualTo("WalletOpened");
            assertThat(diagram.automations().get(0).emitsCommand()).isEqualTo("SendWelcomeNotification");
        }
    }

    @Test
    void unknownKeysUnderDiagramDoNotFailParsing() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                diagram:
                  lanes:
                    - id: main
                      label: Main
                  unknownFutureKey: should-not-fail
                  anotherUnknown:
                    nested: value
                """, EventModel.class);

        assertThat(model.diagram().lanes()).hasSize(1);
        assertThat(model.diagram().lanes().get(0).id()).isEqualTo("main");
    }

    @Test
    void schemaResolverPreservesDiagram() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("wallet-event-model.yaml")) {
            EventModel model = yaml.readValue(in, EventModel.class);
            SchemaResolver resolver = new SchemaResolver();
            EventModel resolved = resolver.resolve(model);

            assertThat(resolved.diagram().lanes()).hasSize(2);
            assertThat(resolved.diagram().assignments()).containsEntry("WalletBalance", "wallet");
            assertThat(resolved.diagram().actors()).hasSize(1);
            assertThat(resolved.diagram().triggers()).hasSize(1);
        }
    }

    @Test
    void deploymentDefaultsWhenMissing() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                """, EventModel.class);

        assertThat(model.deployment().topology()).isEqualTo("monolith");
        assertThat(model.deployment().isDistributed()).isFalse();
        assertThat(model.deployment().commandReplicas()).isEqualTo(2);
        assertThat(model.deployment().keda().enabled()).isFalse();
        assertThat(model.deployment().keda().minReplicas()).isZero();
        assertThat(model.deployment().keda().pollingInterval()).isEqualTo(30);
    }

    @Test
    void deploymentParsesExplicitKedaConfig() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                deployment:
                  topology: distributed
                  commandReplicas: 4
                  keda:
                    enabled: true
                    minReplicas: 1
                    pollingInterval: 15
                """, EventModel.class);

        assertThat(model.deployment().isDistributed()).isTrue();
        assertThat(model.deployment().commandReplicas()).isEqualTo(4);
        assertThat(model.deployment().keda().enabled()).isTrue();
        assertThat(model.deployment().keda().minReplicas()).isEqualTo(1);
        assertThat(model.deployment().keda().pollingInterval()).isEqualTo(15);
    }

    @Test
    void commandFieldValidationKeyIsIgnoredForForwardCompatibleYaml() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands:
                  - name: SubmitX
                    pattern: idempotent
                    produces: [E1]
                    fields:
                      - name: id
                        type: String
                        validation: [notNull, notBlank]
                """, EventModel.class);

        assertThat(model.commands().get(0).fields().get(0).name()).isEqualTo("id");
    }

    @Test
    void deploymentNormalizesInvalidValues() throws Exception {
        EventModel model = yaml.readValue("""
                domain: Demo
                basePackage: com.example.demo
                events: []
                commands: []
                deployment:
                  topology: ""
                  commandReplicas: 0
                  keda:
                    enabled: true
                    minReplicas: -3
                    pollingInterval: 0
                """, EventModel.class);

        assertThat(model.deployment().topology()).isEqualTo("monolith");
        assertThat(model.deployment().commandReplicas()).isEqualTo(2);
        assertThat(model.deployment().keda().minReplicas()).isZero();
        assertThat(model.deployment().keda().pollingInterval()).isEqualTo(30);
    }

    @Test
    void yaviConstraintsCombinations() {
        // exclusiveMinimum only
        assertThat(new FieldSpec("x", "integer", null, 0, null, null, null, null, null, null, null, null).yaviConstraints())
                .isEqualTo(".greaterThan(0)");
        // minimum only
        assertThat(new FieldSpec("x", "integer", 0, null, null, null, null, null, null, null, null, null).yaviConstraints())
                .isEqualTo(".greaterThanOrEqual(0)");
        // minLength: 1 → notBlank shorthand
        assertThat(new FieldSpec("x", "string", null, null, null, null, 1, null, null, null, null, null).yaviConstraints())
                .isEqualTo(".notBlank()");
        // minLength + maxLength
        assertThat(new FieldSpec("x", "string", null, null, null, null, 2, 50, null, null, null, null).yaviConstraints())
                .isEqualTo(".greaterThanOrEqualTo(2).lessThanOrEqualTo(50)");
        // minimum + maximum range
        assertThat(new FieldSpec("x", "integer", 300, null, 850, null, null, null, null, null, null, null).yaviConstraints())
                .isEqualTo(".greaterThanOrEqual(300).lessThanOrEqual(850)");
        // no constraints
        assertThat(new FieldSpec("x", "string", null, null, null, null, null, null, null, null, null, null).yaviConstraints())
                .isEmpty();
    }

    private static FieldSpec scalar(String type) {
        return new FieldSpec("x", type, null, null, null, null, null, null, null, null, null, null);
    }
}
