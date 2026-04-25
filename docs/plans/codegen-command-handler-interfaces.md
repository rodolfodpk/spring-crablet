# Plan: Generate Command Handlers, Automations, and Outbox Publishers as Java Interfaces

**Status: EXECUTED** — implemented and verified (28 tests passing).

## Context

`FileWriterTool.writeGeneratedFiles()` calls `Files.writeString` unconditionally, destroying user
edits on re-generation. Three artifact types contain durable user logic that must be preserved:

- **Command handlers** — business decisions (validation rules, append strategies)
- **Automation handlers** — workflow/reaction policy (condition logic, emitted command mapping)
- **Outbox publishers** — external integration code (HTTP/Kafka clients, auth, payload mapping)

Fix: generate a Java **interface** with default methods for machine-derivable metadata; leave
`decide()`, `publishBatch()`, and `isHealthy()` to the user's `@Component` implementation.

**View projectors are NOT converted.** Their value is the generated concrete SQL + event mapping.
Making them interfaces would force the user to implement almost everything manually.

---

## Summary of Artifact Treatment

| Artifact | Pattern | Generated content |
|----------|---------|-------------------|
| `{Cmd}CommandHandler` | Java interface | empty body, extends Crablet handler sub-interface |
| `{Auto}AutomationHandler` | Java interface | `default getAutomationName()`, `default getEventTypes()`, `default getRequiredTags()` |
| `{Pub}` (OutboxPublisher) | Java interface | `default getName()`, `default getPreferredMode()` |
| `{View}ViewProjector` | Concrete class | unchanged — generator-owned |
| State, StateProjector, QueryPatterns, events | Concrete classes | unchanged — structural generation |

## Implementation Hints Policy

| Artifact | Structural sketch in Javadoc? | Why |
|----------|-------------------------------|-----|
| `{Cmd}CommandHandler` | **Yes** — full `decide()` sketch | Append strategy selection is subtle |
| `{Auto}AutomationHandler` | **No** — "create @Component" note only | Condition logic = business policy |
| `{Pub}` | **No** — "create @Component" note only | Integration code = user-owned |
| `{View}ViewProjector` | **Yes** — full implementation | Behavior is model-derived |
| State / StateProjector / QueryPatterns | **Yes** — full implementation | Structural, model-derived |

---

## Files to Change

### 1. `PlannedArtifact.java`
**Path:** `embabel-codegen/src/main/java/com/crablet/codegen/planning/PlannedArtifact.java`

Add one factory method after `javaClass(...)`:

```java
public static PlannedArtifact javaInterface(String section, String packageName, String interfaceName) {
    return new PlannedArtifact(section, "java", packageName + "." + interfaceName, "Java interface");
}
```

---

### 2. `ArtifactPlanner.java`
**Path:** `embabel-codegen/src/main/java/com/crablet/codegen/planning/ArtifactPlanner.java`

Use `javaInterface` at three call sites. View projectors keep `javaClass`.

```java
// Command handlers:
artifacts.add(PlannedArtifact.javaInterface("command", commandPackage, command.name() + "CommandHandler"));

// Automation handlers:
artifacts.add(PlannedArtifact.javaInterface("automation", automationPackage,
        toJavaIdentifier(automation.name()) + "AutomationHandler"));

// Outbox publishers:
artifacts.add(PlannedArtifact.javaInterface("outbox", outboxPackage,
        toJavaIdentifier(outbox.name())));
```

---

### 3. `ArtifactPlannerTest.java`
**Path:** `embabel-codegen/src/test/java/com/crablet/codegen/planning/ArtifactPlannerTest.java`

**Existing test:** add after the existing `contains("...CommandHandler")` assertion:

```java
assertThat(plan).contains("com.example.loan.command.SubmitLoanApplicationCommandHandler (Java interface)");
assertThat(plan).contains("com.example.loan.command.LoanApplicationState (Java class)");
assertThat(plan).contains("com.example.loan.command.LoanApplicationStateProjector (Java class)");
assertThat(plan).contains("com.example.loan.command.LoanApplicationQueryPatterns (Java class)");
```

**New test:** add a method with a model containing an automation and outbox entry (inline YAML
or fixture at `docs/examples/loan-with-automation-event-model.yaml`) to assert:
- automation handler → `(Java interface)`
- outbox publisher → `(Java interface)`
- view projector → `(Java class)`

---

### 4. `CLAUDE.md`
**Path:** `CLAUDE.md`

Insert three new sections, each **after** its corresponding `### X Template` section (safe ordering
required by `TemplateLoader.load()`, which returns the first `contains()` match):

```
### Command Handler Template            ← load("Command Handler") matches here
### Command Handler Interface Template  ← load("Command Handler Interface") matches here
### View Projector Template
### Automation Handler Template         ← load("Automation Handler") matches here
### Automation Handler Interface Template ← load("Automation Handler Interface") matches here
### Outbox Publisher Template
### Outbox Publisher Interface Template ← load("Outbox Publisher Interface") matches here
```

#### 4a. `### Command Handler Interface Template`

Empty interface body extending the correct Crablet command-handler sub-interface. Only the
sub-interface import is needed; Javadoc sketch uses framework type names without additional imports.

**Key rules for the template text:**
- Must not declare `handle()` or `decide()`; user's `@Component` implements `decide()`
- `handle()` is a framework method — user never overrides it
- Inside Javadoc `<pre>` blocks, write `*/` as `*&#47;`
- Four patterns: idempotent, non-commutative, commutative+guard, pure commutative
- `commutative` vs `pure commutative` is determined by `CommandSpec.guardEvents` (non-empty =
  lifecycle guard; empty = pure). The user prompt already carries `[guard=...]` when `hasGuard()`
  is true, so the LLM can choose the correct `CommandDecision` factory

Examples for the four patterns:

```java
// Idempotent
import com.crablet.command.IdempotentCommandHandler;
/**
 * Handle the SubmitLoanApplication command.
 * <p>Append strategy: idempotent — entity creation.
 * Create a {@code @Component} class implementing this interface.
 * <pre>
 *   LoanApplicationSubmitted event = LoanApplicationSubmitted.of(..., clockProvider.now());
 *   AppendEvent appendEvent = AppendEvent.builder(type(LoanApplicationSubmitted.class))
 *       .tag(APPLICATION_ID, command.applicationId()).data(event).build();
 *   return CommandDecision.Idempotent.of(appendEvent,
 *       type(LoanApplicationSubmitted.class), APPLICATION_ID, command.applicationId());
 * </pre>
 */
public interface SubmitLoanApplicationCommandHandler
        extends IdempotentCommandHandler<SubmitLoanApplication> { }

// Non-commutative
import com.crablet.command.NonCommutativeCommandHandler;
/**
 * Handle the WithdrawFunds command.
 * <p>Append strategy: non-commutative — stream position is the conflict guard.
 * Create a {@code @Component} class implementing this interface.
 * <pre>
 *   Query decisionModel = YourQueryPatterns.decisionModel(command.entityId());
 *   ProjectionResult&lt;YourState&gt; p = eventStore.project(decisionModel, projector);
 *   if (!p.state().isExisting()) throw new YourNotFoundException(command.entityId());
 *   AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
 *       .tag(TAG, command.entityId()).data(event).build();
 *   return CommandDecision.NonCommutative.of(appendEvent, decisionModel, p.streamPosition());
 * </pre>
 */
public interface WithdrawFundsCommandHandler
        extends NonCommutativeCommandHandler<WithdrawFunds> { }

// Commutative with lifecycle guard
import com.crablet.command.CommutativeCommandHandler;
/**
 * Handle the DepositFunds command.
 * <p>Append strategy: commutative with lifecycle guard.
 * Create a {@code @Component} class implementing this interface.
 * <pre>
 *   Query lifecycleModel = YourQueryPatterns.lifecycleModel(command.entityId());
 *   ProjectionResult&lt;YourState&gt; p = eventStore.project(lifecycleModel, projector);
 *   if (!p.state().isExisting()) throw new YourNotFoundException(command.entityId());
 *   AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
 *       .tag(TAG, command.entityId()).data(event).build();
 *   Query guard = YourQueryPatterns.lifecycleGuard(command.entityId());
 *   return CommandDecision.CommutativeGuarded.withLifecycleGuard(appendEvent, guard, p.streamPosition());
 * </pre>
 */
public interface DepositFundsCommandHandler
        extends CommutativeCommandHandler<DepositFunds> { }

// Pure commutative (no guard)
import com.crablet.command.CommutativeCommandHandler;
/**
 * Handle the RecordPageView command.
 * <p>Append strategy: pure commutative — no lifecycle check.
 * Create a {@code @Component} class implementing this interface.
 * <pre>
 *   AppendEvent appendEvent = AppendEvent.builder(type(PageViewRecorded.class))
 *       .tag(PAGE_ID, command.pageId()).data(event).build();
 *   return CommandDecision.Commutative.of(appendEvent);
 * </pre>
 */
public interface RecordPageViewCommandHandler
        extends CommutativeCommandHandler<RecordPageView> { }
```

#### 4b. `### Automation Handler Interface Template`

Metadata-only interface. No `decide()`. **No structural sketch** — condition logic and command
mapping are business policy. Javadoc says only: "Create a `@Component` class implementing this
interface and provide `decide()` logic."

`getRequiredTags()` default: `Set.of()`. If non-empty, use string literals, not tag constants.

```java
import com.crablet.automations.AutomationHandler;
import com.example.loan.domain.LoanApplicationSubmitted;
import java.util.Set;
import static com.crablet.eventstore.EventType.type;

/**
 * React to LoanApplicationSubmitted and emit SendWelcomeEmailCommand.
 * <p>Create a {@code @Component} class implementing this interface and provide {@code decide()} logic.
 */
public interface SendWelcomeEmailAutomationHandler extends AutomationHandler {
    @Override default String getAutomationName() { return "send-welcome-email"; }
    @Override default Set<String> getEventTypes() { return Set.of(type(LoanApplicationSubmitted.class)); }
    @Override default Set<String> getRequiredTags() { return Set.of(); }
}
```

#### 4c. `### Outbox Publisher Interface Template`

Metadata-only interface. No `publishBatch()` or `isHealthy()`. **No structural sketch** —
integration code is user-owned. Javadoc says only: "Create a `@Component` class and provide
`publishBatch()` and `isHealthy()` logic."

`PublishMode` is a **nested enum on `OutboxPublisher`**. Once the interface extends
`OutboxPublisher`, `PublishMode` resolves as an inherited member type — no separate import needed.
Do NOT import `com.crablet.outbox.PublishMode` (that standalone class does not exist).

```java
import com.crablet.outbox.OutboxPublisher;

/**
 * Publish loan events over HTTP webhook.
 * <p>Create a {@code @Component} class implementing this interface and provide
 * {@code publishBatch()} and {@code isHealthy()} logic.
 */
public interface LoanNotificationsPublisher extends OutboxPublisher {
    @Override default String getName() { return "loan-notifications"; }
    @Override default PublishMode getPreferredMode() { return PublishMode.INDIVIDUAL; }
}
```

---

### 5. `CommandsAgent.java`
**Path:** `embabel-codegen/src/main/java/com/crablet/codegen/agents/CommandsAgent.java`

**Change A:** `templates.load("Command Handler")` → `templates.load("Command Handler Interface")`

**Change B — system prompt — two edits:**

**(i) Replace the existing append-strategy mapping** (currently says
`commutative → ... always uses lifecycle guard`). Note: these are Crablet append strategies
on top of a DCB-capable EventStore, not DCB patterns themselves.

```
Command append strategy:
- idempotent      → IdempotentCommandHandler — entity creation (first event)
- commutative     → CommutativeCommandHandler — two variants, distinguished by guardEvents field:
                      guardEvents non-empty → CommutativeGuarded.withLifecycleGuard
                      guardEvents empty    → Commutative.of(appendEvent) (pure, no guard)
- non-commutative → NonCommutativeCommandHandler — order-matters operations
```

The user prompt already carries `[guard=...]` in the per-command description when
`CommandSpec.hasGuard()` is true. The LLM uses this to choose the correct `CommandDecision` factory.

**(ii) Add after the YAVI block:**
```
Command handler files are Java INTERFACES — no class body, no @Component, no handle() method,
and no decide() declaration. Inherit decide() from the selected sub-interface.
Do not generate implementation classes or any additional @Component handler files.
Include a Javadoc comment with:
  - one-line description of the command
  - the append strategy name and its rationale
  - a <pre> structural sketch showing a complete decide() implementation
  - a note to create a @Component class implementing this interface in a separate file
In <pre> blocks inside Javadoc, write */ as *&#47;.
```

**Change C — user prompt item 4:**
```
4. One CommandHandler Java INTERFACE per command — empty interface body, no @Component.
   Extend IdempotentCommandHandler, NonCommutativeCommandHandler, or CommutativeCommandHandler
   based on the append strategy. CommutativeCommandHandler covers both pure commutative and
   commutative-with-lifecycle-guard; choose the CommandDecision factory from [guard=...] hint.
   The interface file must compile with only necessary imports. Javadoc sketch uses framework
   type names (EventType.type, AppendEvent.builder, CommandDecision factories).
   Write */ inside <pre> blocks as *&#47;.
```

---

### 6. `AutomationsAgent.java`
**Path:** `embabel-codegen/src/main/java/com/crablet/codegen/agents/AutomationsAgent.java`

**Change A:** `templates.load("Automation Handler")` → `templates.load("Automation Handler Interface")`

**Change B — system prompt:** **Replace** the entire Key rules block (which currently lists
`decide()`, `JdbcTemplate`, `Translate condition`, `Return AutomationDecision`, `ObjectMapper`) with:

```
Key rules:
- Generate a Java INTERFACE, not a @Component class.
- Extend AutomationHandler.
- Declare three default methods: getAutomationName(), getEventTypes(), getRequiredTags().
- Do NOT declare decide(). It is inherited and must be implemented by the user.
- Do NOT generate implementation classes or any @Component handler files.
- getRequiredTags() must always return Set.of(). AutomationSpec has no requiredTags field;
  if non-empty is ever needed, use string literals, NOT tag constant class references.
- Javadoc: "Create a @Component class implementing this interface to provide decide() logic."
  Do NOT mention condition translation, JdbcTemplate, ObjectMapper, or emitted command mapping.
```

**Change C — user prompt:** Change the closing generate line to:
`"Generate %sAutomationHandler.java as a Java interface extending AutomationHandler."`

Remove the `viewSpec` lookup block and its prompt branch (the section that computes `viewSpec`
from `model.viewNamed(automation.readsView())` and appends view table/fields to the user prompt).
The generated interface has no view fields; that code path is dead for interface generation.
Remove the `ViewSpec` import from the class as well.

---

### 7. `OutboxAgent.java`
**Path:** `embabel-codegen/src/main/java/com/crablet/codegen/agents/OutboxAgent.java`

**Change A:** `templates.load("Outbox Publisher")` → `templates.load("Outbox Publisher Interface")`

**Change B — system prompt:** **Replace** the entire Key rules block (which currently lists
`publishBatch()`, `ObjectMapper`, smtp/http/kafka adapter field rules, `getPreferredMode()`,
`isHealthy()`) with:

```
Key rules:
- Generate a Java INTERFACE, not a @Component class.
- Extend OutboxPublisher.
- Declare two default methods: getName() and getPreferredMode().
- Do NOT declare publishBatch() or isHealthy(). They must be implemented by the user.
- Do NOT generate implementation classes or any @Component publisher files.
- PublishMode is a nested enum on OutboxPublisher; it resolves as an inherited member type.
  Do NOT import com.crablet.outbox.PublishMode — that standalone class does not exist.
  Only import com.crablet.outbox.OutboxPublisher.
- getPreferredMode() hint: HTTP → PublishMode.INDIVIDUAL, Kafka → PublishMode.BATCH, else INDIVIDUAL.
- Javadoc: "Create a @Component class implementing this interface to provide publishBatch()
  and isHealthy() logic." Do NOT mention clients, event iteration, retry, or adapter details.
```

**Change C — user prompt:** Change the closing generate line to:
`"Generate %s.java as a Java interface extending OutboxPublisher."`

Keep `adapter type` in the user prompt metadata — needed to derive `getPreferredMode()`.
Remove adapter **implementation** hints: SmtpEmailService/RestClient/KafkaTemplate fields,
`publishBatch()` iteration rules, `isHealthy()` connectivity check rules.

---

### 8. `docs/examples/submit-loan-application-claude-dialogue.md`
**Path:** `docs/examples/submit-loan-application-claude-dialogue.md`

- Step 4 artifact plan: `SubmitLoanApplicationCommandHandler (Java interface)`
- Step 5 output list: `SubmitLoanApplicationCommandHandler (Java interface — implement in a separate @Component class)`
- After the "Verification passed" block: add a note that the generated interface is safe to
  overwrite; other generated artifacts (StateProjector, views) may overwrite manual edits.

---

### 9. `embabel-codegen/README.md`
**Path:** `embabel-codegen/README.md`

Replace the "Generated code compiles but behaviour is wrong" paragraph to cover:
- Command handler, automation handler, outbox publisher → interfaces; user writes `@Component`
- View projectors, StateProjector, QueryPatterns, State records → concrete generator-owned classes

---

## Files NOT Changing

| File | Reason |
|------|--------|
| `FileWriterTool.java` | No skip-if-exists needed; interfaces have no user logic |
| `CodegenPipeline.java` | No structural change |
| `EventsAgent.java` | Generates sealed interface + records; unchanged |
| `ViewsAgent.java` | Views stay concrete |

---

## Verify-While-Coding Notes

- **AutomationsAgent `viewSpec` removal:** confirm the `readsView` / `viewSpec` block is used
  only to construct prompt text for the old concrete handler. If it's used for any non-generation
  purpose (logging, planning), adjust accordingly.
- **Line numbers:** the plan references approximate positions in agents
  (`CommandSpec.hasGuard()` section, `viewSpec` lookup block). Re-check actual locations at
  implementation time — they may have shifted.
- **`commutative` YAML convention:** if `commutative` without a `guardEvents` list was previously
  always assumed to mean "with guard," consider adding a note to `docs/EVENT_MODEL_FORMAT.md`
  clarifying that `guardEvents: []` means pure commutative.

---

## Verification

1. `./mvnw test -pl embabel-codegen` — `ArtifactPlannerTest` passes including:
   - `"com.example.loan.command.SubmitLoanApplicationCommandHandler (Java interface)"`
   - `"com.example.loan.command.LoanApplicationState (Java class)"`
   - New test: automation and outbox → `(Java interface)`; view projector → `(Java class)`
2. `TemplateLoader`: `load("X Interface")` cannot match `### X Template` — `"X Template"` does
   not contain `"X Interface"`. Correct ordering (X Template before X Interface Template) ensures
   `load("X")` finds the right section.
3. Run manual generation against a fixture with command + automation + outbox entries
   (add `docs/examples/loan-with-automation-event-model.yaml` if needed). Confirm:
   - command handlers: empty interface body, Javadoc `decide()` sketch, no `@Component`
   - automation handlers: three default methods, no `decide()`, no structural sketch
   - outbox publishers: two default methods, no `publishBatch()`/`isHealthy()`, no structural sketch
   - no separate `@Component` implementation file generated for any of the three types
   - outbox: only `OutboxPublisher` imported; `PublishMode` used as inherited member name
   - automation: `getRequiredTags()` returns `Set.of()`
4. Compile the generated sample app from its output directory with `./mvnw compile`.
