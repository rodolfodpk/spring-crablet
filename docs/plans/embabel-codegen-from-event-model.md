# Embabel Codegen from Event Model — Plan

Generate spring-crablet code (views, automations, outbox, command handlers) from a structured
event model YAML produced by the `event-modeling` skill. The goal is zero TODOs in generated
code — anything structural is generated; only external system credentials and business rules
absent from the model remain for the developer.

## Unified user experience

There is **one product story** for people adopting Crablet with codegen, whether they start
from an empty directory or an existing service:

1. **Optional bootstrap** — a greenfield app that can compile with Crablet on the classpath
   (Spring Boot + the right `pom.xml` / Gradle coordinates, minimal `main`, Flyway/Postgres
   hooks if we standardize on them, empty `src/main/java/.../domain` for generated code).
2. **Event modeling** — the 7-step `event-modeling` workshop in Claude Code.
3. **Generate** — run the CLI against `event-model.yaml` into the app’s `src` (or a module path).
4. **Build** — `mvn` / `make install` in the app until green.

**Brownfield** users skip step 1 and point `--output` at their existing module. The docs and
help text use the **same ordered checklist** for both: only step 1 is “if you need a new app.”

**Single CLI surface (implementation split is OK).** The fat JAR exposes **subcommands** so
the journey is not “install codegen” vs “create a project” in two different tools:

| Subcommand | Role |
|---|---|
| `init` | Create or patch a Spring Boot project so Crablet dependencies and a minimal layout exist; may call the Spring Initializr API with fixed Boot/Java versions aligned to this repo, or expand an embedded template. |
| `generate` | Read `event-model.yaml` and run the agent pipeline (unchanged). |

Users never need to discover a separate “Crablet starter” feature — `init` and `generate` are
**one entry point** (`java -jar embabel-codegen.jar help`). MCP wraps the same operations:
`@Tool` for `init` and `generate` so Claude Code can run the full path without leaving the
session.

**Documentation** should mirror the CLI: one primary page, “From zero to generated domain,”
with **optional** `init` at the top and **required** workshop + `generate` + build below.

## Consumption model

Start as a **CLI** (Spring Shell fat JAR) with **`init` + `generate`**. Once code generation
quality is validated, wrap as an **MCP server** so Claude Code can call it natively without
leaving the session.

**Greenfield (unified path):**

```
# 1) Optional — new repo / directory with Crablet-ready Boot app
java -jar embabel-codegen/target/embabel-codegen.jar init \
  --name my-service --package com.example.myservice --dir ../my-service
  → pom.xml (or build.gradle) + Crablet BOM/modules + minimal @SpringBootApplication

# 2) Event modeling
/event-modeling Wallet domain   ← Claude Code skill (7-step workshop)
  → event-model.yaml

# 3) Codegen
java -jar embabel-codegen/target/embabel-codegen.jar generate \
  --model event-model.yaml --output ../my-service/src/main/java
  → files written + compile-and-fix loop

# 4) Build
cd ../my-service && ./mvnw verify   # or make install if your app uses the repo Makefile
```

**Brownfield:** omit `init`; use `generate --output` pointing at the existing source root or
`src/main/java`.

MCP upgrade path: annotate each agent with `@Tool`, expose `init` and `generate` via Spring AI
MCP server, add entry to `.claude/settings.json` — the standalone CLI remains useful for
CI and for users not in Claude Code.

---

## Location in the repo

`embabel-codegen` lives inside this repository, excluded from the Maven reactor — the same
treatment as `wallet-example-app` and `shared-examples-domain`.

```
spring-crablet/
├── crablet-eventstore/
├── crablet-commands/
├── crablet-views/
├── crablet-automations/
├── crablet-outbox/
├── shared-examples-domain/     ← excluded from reactor
├── wallet-example-app/         ← excluded from reactor
└── embabel-codegen/            ← excluded from reactor, same pattern
```

The root `pom.xml` does **not** list `embabel-codegen` in `<modules>`. It is built via dedicated
Makefile targets:

```makefile
codegen-install:
	./mvnw install -pl embabel-codegen -am

codegen-build:
	./mvnw package -pl embabel-codegen -DskipTests
	# produces embabel-codegen/target/embabel-codegen.jar
```

**Language: Java** — consistent with the rest of the codebase. Spring Shell and Spring AI work
fine in Java; Embabel is used only as a runtime dependency, not as a language requirement.

**Templates loaded from CLAUDE.md directly** — no copy-paste drift:

```java
// TemplateLoader.java
public String load(String artifact) {
    String claudeMd = Path.of("../CLAUDE.md").toAbsolutePath().readString();
    return extractSection(claudeMd, "### " + artifact + " Template");
}
```

**Ground truth validation** — the Wallet domain in `shared-examples-domain` is the known-correct
implementation. Codegen quality is validated by generating from a wallet `event-model.yaml` and
diffing against the existing code.

---

## What is genuinely not generable

Only two things cannot be inferred from the event model:

1. **Business rules not captured in the model** — e.g. "VIP customers get a different rate."
   Fix: put it in the model (as a `condition:` expression), not leave it as a TODO.
2. **External system credentials and endpoints** — SMTP server, Kafka broker address.
   These belong in `application.yml`, not generated code.

Everything else — state projectors, view lookups, automation conditions, outbox adapters — is
structural and fully generable from a sufficiently rich YAML spec.

---

## Feasibility by artifact

| Artifact | Feasibility | What enables it |
|---|---|---|
| Events (sealed interface + records) | ~95% | Fields + tags in model |
| Command records + YAVI validation | ~95% | Fields from model, patterns formulaic |
| `StateProjector` + state record | ~90% | State transitions inferred from `produces:` per command |
| Command handler implementations | ~85% | DCB pattern from `pattern:` field; state from projector |
| `AbstractTypedViewProjector` + SQL migration | ~90% | Template prescriptive; fields + tag in model |
| `AutomationHandler` implementations | ~90% | `condition:` → if-statement; `readsView:` → JdbcTemplate lookup |
| `OutboxPublisher` implementations | ~85% | `adapter:` names the interface; event fields drive deserialization |
| Unit test stubs (Given-When-Then) | ~95% | Mechanical from scenarios in model |

The remaining gap (~10-15%) is always business logic absent from the model, never framework plumbing.

---

## Event model YAML format

The YAML must be rich enough to generate everything without TODOs. Key additions over a minimal
format: typed fields, YAVI validation constraints, shared schemas, `condition`, `readsView`,
and `adapter`.

### Typed fields

Fields carry explicit types — the codegen never guesses. Events have no validation (they are
facts); commands carry YAVI constraints per field.

```yaml
domain: LoanApplication
basePackage: com.example.loan

events:
  - name: LoanApplicationSubmitted
    tags: [application_id, customer_id]
    fields:
      - name: applicationId
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: purpose
        type: String

  - name: CreditScoreChecked
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: score
        type: int
      - name: provider
        type: String

  - name: LoanApplicationApproved
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: approvedAmount
        type: int
      - name: approvedBy
        type: String

  - name: LoanApplicationRejected
    tags: [application_id]
    fields:
      - name: applicationId
        type: String
      - name: reason
        type: String
      - name: rejectedBy
        type: String

commands:
  - name: SubmitLoanApplication
    pattern: idempotent
    produces: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: customerId
        type: String
        validation: [notNull, notBlank]
      - name: amount
        type: int
        validation: greaterThan(0)
      - name: purpose
        type: String
        validation: [notNull, notBlank]

  - name: RecordCreditScore
    pattern: commutative
    produces: [CreditScoreChecked]
    guardEvents: [LoanApplicationSubmitted]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: score
        type: int
        validation: between(300, 850)
      - name: provider
        type: String
        validation: [notNull, notBlank]

  - name: ApproveLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationApproved]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: approvedAmount
        type: int
        validation: greaterThan(0)
      - name: approvedBy
        type: String
        validation: [notNull, notBlank]

  - name: RejectLoanApplication
    pattern: non-commutative
    produces: [LoanApplicationRejected]
    fields:
      - name: applicationId
        type: String
        validation: [notNull, notBlank]
      - name: reason
        type: String
        validation: [notNull, notBlank]
      - name: rejectedBy
        type: String
        validation: [notNull, notBlank]

views:
  - name: LoanApplicationReview
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved, LoanApplicationRejected]
    tag: application_id
    fields:
      - name: applicationId
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: creditScore
        type: int
      - name: status
        type: String

  - name: PendingAutoApprovals
    reads: [LoanApplicationSubmitted, CreditScoreChecked, LoanApplicationApproved]
    tag: application_id
    fields:
      - name: applicationId
        type: String
      - name: amount
        type: int
      - name: creditScore
        type: int
      - name: status
        type: String

automations:
  - name: AutoApproveSmallLoans
    triggeredBy: CreditScoreChecked
    emitsCommand: ApproveLoanApplication
    pattern: todo-list
    condition: "amount <= 5000 AND creditScore >= 700"  # → generates if-statement
    readsView: PendingAutoApprovals                     # → generates JdbcTemplate lookup

outbox:
  - name: EmailNotificationPublisher
    topic: loan-notifications
    handles: [LoanApplicationApproved, LoanApplicationRejected]
    adapter: smtp                                       # → generates SmtpEmailService delegate
```

### Shared schemas (avoid duplication)

Commands and events often share the same fields. A top-level `schemas:` block lets both
reference one definition — commands add `validation:` on top.

```yaml
schemas:
  - name: LoanApplicationData
    fields:
      - name: applicationId
        type: String
      - name: customerId
        type: String
      - name: amount
        type: int
      - name: purpose
        type: String

events:
  - name: LoanApplicationSubmitted
    tags: [application_id, customer_id]
    schema: LoanApplicationData       # inherits all fields, no validation

commands:
  - name: SubmitLoanApplication
    pattern: idempotent
    produces: [LoanApplicationSubmitted]
    schema: LoanApplicationData       # inherits fields, adds validation
    validation:
      applicationId: [notNull, notBlank]
      customerId: [notNull, notBlank]
      amount: greaterThan(0)
      purpose: [notNull, notBlank]
```

Schema resolution happens as a preprocessing step in `CodegenPipeline` before any agent runs —
schemas are fully inlined into each spec so agents never see unresolved references.

### Supported type vocabulary

Kept small and explicit. The `EventModel` deserializer rejects unknown types at startup — fail
fast before generation begins.

| YAML type | Java type | YAVI method |
|---|---|---|
| `String` | `String` | `._string(...)` |
| `int` | `int` | `._integer(...)` |
| `long` | `long` | `._long(...)` |
| `BigDecimal` | `BigDecimal` | `._bigDecimal(...)` |
| `boolean` | `boolean` | `._boolean(...)` |
| `UUID` | `UUID` | `._string(...)` + UUID pattern |
| `Instant` | `Instant` | no YAVI constraint (framework handles) |

### YAVI constraint mapping

```
[notNull]            → c -> c.notNull()
[notNull, notBlank]  → c -> c.notNull().notBlank()
greaterThan(0)       → c -> c.greaterThan(0)
between(300, 850)    → c -> c.between(300, 850)
```

Maps directly to the YAVI template already in CLAUDE.md — no custom constraint language needed.

### State transition inference (no manual StateProjector)

The agents infer `StateProjector` from `produces:` across all commands:

| Event produced by | Inferred state transition |
|---|---|
| `SubmitLoanApplication` → `LoanApplicationSubmitted` | `isExisting = true`, `status = PENDING` |
| `ApproveLoanApplication` → `LoanApplicationApproved` | `isAlreadyDecided = true`, `status = APPROVED` |
| `RejectLoanApplication` → `LoanApplicationRejected` | `isAlreadyDecided = true`, `status = REJECTED` |

The `CommandsAgent` generates the `LoanApplicationState` record and its `StateProjector` before
generating the handler that depends on them.

---

## Project structure

```
embabel-codegen/                        # inside spring-crablet repo, excluded from reactor
├── pom.xml                             # parent = spring-crablet root, not in <modules>
└── src/main/java/com/crablet/codegen/
    ├── CodegenApp.java                 # @SpringBootApplication + Spring Shell
    ├── CodegenCommand.java             # @ShellComponent: generate
    ├── bootstrap/                      # Spring Initializr client and/or POM template (init)
    │   ├── InitCommand.java            # @ShellComponent: init
    │   └── InitService.java            # project creation, separate from CodegenPipeline
    ├── model/
    │   ├── EventModel.java             # top-level YAML root
    │   ├── FieldSpec.java              # name + type + validation (shared by events and commands)
    │   ├── SchemaSpec.java             # reusable field group
    │   ├── EventSpec.java              # event definition
    │   ├── CommandSpec.java            # command + DCB pattern + YAVI constraints
    │   ├── ViewSpec.java               # view projector + SQL migration
    │   ├── AutomationSpec.java         # automation handler + condition + readsView
    │   └── OutboxSpec.java             # outbox publisher + adapter type
    ├── tools/
    │   ├── FileWriterTool.java         # write generated files to output dir
    │   ├── MavenTool.java              # run mvn compile, return structured errors
    │   └── TemplateLoader.java         # load prompt templates directly from CLAUDE.md
    ├── agents/
    │   ├── EventsAgent.java            # sealed interface + event records
    │   ├── CommandsAgent.java          # command records + StateProjector + handler impls
    │   ├── ViewsAgent.java             # AbstractTypedViewProjector + SQL migration
    │   ├── AutomationsAgent.java       # AutomationHandler impls (condition + view lookup)
    │   ├── OutboxAgent.java            # OutboxPublisher impls (adapter delegate)
    │   └── RepairAgent.java            # compile-error → fix loop
    └── pipeline/
        └── CodegenPipeline.java        # orchestrates agents in order
```

---

## Pipeline — orchestration

```java
@Component
public class CodegenPipeline {

    public void run(EventModel model, Path outputDir) {
        // 0. Preprocessing — resolve schemas, validate field types
        EventModel resolved = schemaResolver.resolve(model); // inlines schema: references
        fieldTypeValidator.validate(resolved);               // rejects unknown types fast

        // Sequential — each step depends on prior output
        eventsAgent.generate(resolved, outputDir);      // sealed interface + records
        commandsAgent.generate(resolved, outputDir);    // StateProjector then handlers
        viewsAgent.generate(resolved, outputDir);       // projectors + SQL migrations
        automationsAgent.generate(resolved, outputDir); // condition + view lookup
        outboxAgent.generate(resolved, outputDir);      // adapter delegate

        // Compile-and-fix loop (max 3 attempts)
        for (int attempt = 1; attempt <= 3; attempt++) {
            CompileResult result = maven.compile(outputDir);
            if (result.success()) return;
            System.out.printf("Attempt %d: %d errors — repairing...%n",
                attempt, result.errors().size());
            repairAgent.fix(result.errors(), outputDir);
        }
    }
}
```

`CommandsAgent` generates `StateProjector` before command handlers — handlers depend on it.
Schema resolution runs before all agents — agents always receive fully inlined `FieldSpec` lists.

---

## Agent pattern (Views example)

```java
@Component
public class ViewsAgent {

    private final ChatClient chatClient;   // Spring AI; Embabel wraps this
    private final FileWriterTool fileWriter;
    private final TemplateLoader templates;

    public void generate(EventModel model, Path outputDir) {
        String systemPrompt = templates.load("AbstractTypedViewProjector"); // from CLAUDE.md

        for (ViewSpec view : model.views()) {
            String code = chatClient.prompt()
                .system(systemPrompt)
                .user("""
                    Generate a complete AbstractTypedViewProjector implementation for:
                    %s

                    Domain package: %s
                    Available event types: %s

                    Also generate the Flyway SQL migration for the view table.
                    Return two files: %sProjector.java and V{n}__create_%s.sql
                    """.formatted(
                        view.toYaml(),
                        model.basePackage(),
                        model.eventNames(),
                        view.name(),
                        view.tableName()))
                .call()
                .content();

            fileWriter.writeGeneratedFiles(code, outputDir);
        }
    }
}
```

Key principle: `TemplateLoader` extracts the `AbstractTypedViewProjector` section directly from
the repo's own `CLAUDE.md`. CLAUDE.md is the single source of truth — no separate prompt files
to keep in sync.

---

## Automation agent (condition + view lookup)

```java
@Component
public class AutomationsAgent {

    public void generate(EventModel model, Path outputDir) {
        String systemPrompt = templates.load("AutomationHandler"); // from CLAUDE.md

        for (AutomationSpec automation : model.automations()) {
            // readsView resolves to the generated view table — no TODO
            ViewSpec viewSpec = model.viewNamed(automation.readsView());

            String code = chatClient.prompt()
                .system(systemPrompt)
                .user("""
                    Generate a complete AutomationHandler for:
                    %s

                    View to read: %s
                    Table name: %s
                    Condition expression: %s
                    Command to emit: %s

                    Generate:
                    1. JdbcTemplate lookup against %s by the trigger event's tag
                    2. If-statement translating condition: %s
                    3. Return AutomationDecision.ExecuteCommand or AutomationDecision.Ignore
                    """.formatted(
                        automation.toYaml(),
                        viewSpec.toYaml(),
                        viewSpec.tableName(),
                        automation.condition(),
                        automation.emitsCommand(),
                        viewSpec.tableName(),
                        automation.condition()))
                .call()
                .content();

            fileWriter.writeGeneratedFiles(code, outputDir);
        }
    }
}
```

---

## Repair agent

```java
@Component
public class RepairAgent {

    public void fix(List<CompileError> errors, Path outputDir) {
        errors.stream()
            .collect(Collectors.groupingBy(CompileError::file))
            .forEach((file, fileErrors) -> {
                String fixed = chatClient.prompt()
                    .system("Fix Java compilation errors. Return only the corrected file content.")
                    .user("""
                        File: %s
                        Errors:
                        %s

                        Current content:
                        %s
                        """.formatted(
                            file.getFileName(),
                            fileErrors.stream()
                                .map(e -> "Line %d: %s".formatted(e.line(), e.message()))
                                .collect(Collectors.joining("\n")),
                            Files.readString(file)))
                    .call()
                    .content();
                Files.writeString(file, fixed);
            });
    }
}
```

---

## CLI entry point

`init` and `generate` live in the same Spring Shell application. `InitService` (Initializr
HTTP client or template expansion) is separate from `CodegenPipeline` to keep release and
test boundaries clear, but **the user only runs one JAR**.

```java
@ShellComponent
public class InitCommand {

    private final InitService initService;

    @ShellMethod(key = "init", value = "Bootstrap a Spring Boot app with Crablet dependencies")
    public void init(
        @ShellOption("--name") String artifactId,
        @ShellOption("--package") String basePackage,
        @ShellOption("--dir") String targetDir
    ) {
        initService.createProject(artifactId, basePackage, Path.of(targetDir));
        System.out.println("Next: run the event-modeling workshop, then: generate --model event-model.yaml --output ...");
    }
}

@ShellComponent
public class CodegenCommand {

    private final CodegenPipeline pipeline;

    @ShellMethod(key = "generate", value = "Generate code from an event model YAML")
    public void generate(
        @ShellOption("--model") String modelPath,
        @ShellOption(value = "--output", defaultValue = ".") String outputPath
    ) throws Exception {
        EventModel model = new ObjectMapper(new YAMLFactory())
            .readValue(new File(modelPath), EventModel.class);
        pipeline.run(model, Path.of(outputPath));
        System.out.println("Done. Build the application module (e.g. ./mvnw verify).");
    }
}
```

---

## What needs to be added to CLAUDE.md

Two templates currently missing (needed as prompt sources for the agents):

1. **AutomationHandler template** — no example exists in CLAUDE.md today
2. **OutboxPublisher template** — no example exists in CLAUDE.md today

Add these to CLAUDE.md first; `prompts/automations.txt` and `prompts/outbox.txt` then load them.

## What needs to change in the event-modeling skill

**Done.** The skill has been updated to follow the full 7-step methodology from
https://jeasthamdev.medium.com/event-modeling-by-example-c6a4ccb4ddf6

Changes made to `.claude/skills/event-modeling/SKILL.md`:

- **Step 1** — single one-sentence opener instead of 3 pre-scoping questions; entities and
  external systems emerge from events, not from pre-loaded assumptions
- **Step 4 — Storyline (swimlanes)** — added before commands; actor type (customer / staff /
  system / time) determines whether a trigger becomes `CommandHandler`, `AutomationHandler`,
  or `OutboxPublisher` — load-bearing for code generation
- **Step 5** — field types and YAVI constraints collected per field; shared schema proposed
  when command and event share the same fields
- **Step 6 — Service grouping** — added; skippable for single-service, always offered
- **Step 8** — explicit `event-model.yaml` emit step with full typed field format
- **DCB pattern selection table** — maps actor type from Step 4 to the correct
  `idempotent / commutative / non-commutative` pattern
- **Facilitation rules** — "never ask about entities upfront", "swimlanes before commands"

---

## Rollout sequence

1. ~~Add `AutomationHandler` + `OutboxPublisher` templates to CLAUDE.md (prompt sources)~~ **Done**
2. ~~Enhance `event-modeling` skill to emit `event-model.yaml` with full field set~~ **Done** — skill updated to follow all 7 steps + emit `event-model.yaml`
3. ~~Create `embabel-codegen/` module in this repo (excluded from reactor, Java, Spring Shell + Spring AI)~~ **Done** — all agents, pipeline, CLI, model, tools implemented; fat JAR builds to 29MB; `help` and `init` work without `ANTHROPIC_API_KEY`; `generate` fails fast with a clear message when key is absent
4. ~~Add `codegen-build` and `codegen-install` targets to `Makefile`~~ **Done**
5. ~~Unified UX — document “From zero to generated domain”~~ **Done** — `docs/CREATE_A_CRABLET_APP.md` restructured to lead with the AI-first path (`init` → `/event-modeling` → `generate` → `./mvnw verify`); manual path kept as secondary section; MCP shortcut documented
6. ~~Validate YAML parsing and schema resolution on the Wallet domain~~ **Done** — `embabel-codegen/src/test/resources/wallet-event-model.yaml` added; 2 tests pass (parsing + schema inlining); CLI reaches EventsAgent before failing on missing API key; full generation requires `ANTHROPIC_API_KEY` to be set:
   ```bash
   export ANTHROPIC_API_KEY=sk-...
   cd embabel-codegen
   java -jar target/embabel-codegen.jar generate \
     --model src/test/resources/wallet-event-model.yaml \
     --output /tmp/wallet-gen
   ```
7. ~~Expose `init` + `generate` as MCP tools~~ **Done** — `McpServer.java` implements MCP 2024-11-05 stdio protocol; `embabel_init` and `embabel_generate` tools exposed; `.claude/settings.json` added to repo root; Spring Boot banner + INFO logs suppressed via JVM flags so the protocol stream is clean; verified with live `tools/list` and `tools/call embabel_init` round-trips
