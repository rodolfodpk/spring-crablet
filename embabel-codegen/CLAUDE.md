# embabel-codegen

Generator guidance for the AI codegen pipeline. Templates below define the exact shape of generated
Java interfaces; implementation classes are written by the user separately.

### Command Handler Interface Template

The generator produces a Java **interface** for each command handler. The interface has an empty
body, no `@Component`, no `handle()` method, and no `decide()` declaration. It extends the correct
Crablet command-handler sub-interface. The user's separate `@Component` implementation class
provides `decide()`.

Inside Javadoc `<pre>` blocks, write `*/` as `*&#47;` to avoid closing the comment early.

Append strategy mapping:
- `idempotent` → `IdempotentCommandHandler<C>`
- `non-commutative` → `NonCommutativeCommandHandler<C>`
- `commutative` with `guardEvents` → `CommutativeCommandHandler<C>` using `CommutativeGuarded`
- `commutative` without `guardEvents` → `CommutativeCommandHandler<C>` using pure `Commutative`

**Idempotent**:
```java
import com.crablet.command.IdempotentCommandHandler;

/**
 * Handle the SubmitLoanApplication command.
 *
 * <p>Append strategy: idempotent — entity creation.
 * Create a {@code @Component} class implementing this interface.
 *
 * <pre>
 *   LoanApplicationSubmitted event = LoanApplicationSubmitted.of(..., clockProvider.now());
 *   AppendEvent appendEvent = AppendEvent.builder(type(LoanApplicationSubmitted.class))
 *       .tag(APPLICATION_ID, command.applicationId()).data(event).build();
 *   return CommandDecision.Idempotent.of(appendEvent,
 *       type(LoanApplicationSubmitted.class), APPLICATION_ID, command.applicationId());
 * </pre>
 */
public interface SubmitLoanApplicationCommandHandler
        extends IdempotentCommandHandler<SubmitLoanApplication> {
}
```

**Non-commutative**:
```java
import com.crablet.command.NonCommutativeCommandHandler;

/**
 * Handle the WithdrawFunds command.
 *
 * <p>Append strategy: non-commutative — stream position is the conflict guard.
 * Create a {@code @Component} class implementing this interface.
 *
 * <pre>
 *   Query decisionModel = YourQueryPatterns.decisionModel(command.entityId());
 *   ProjectionResult&lt;YourState&gt; projection = eventStore.project(decisionModel, projector);
 *   if (!projection.state().isExisting()) throw new YourNotFoundException(command.entityId());
 *   AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
 *       .tag(TAG, command.entityId()).data(event).build();
 *   return CommandDecision.NonCommutative.of(appendEvent, decisionModel, projection.streamPosition());
 * </pre>
 */
public interface WithdrawFundsCommandHandler
        extends NonCommutativeCommandHandler<WithdrawFunds> {
}
```

**Commutative with lifecycle guard**:
```java
import com.crablet.command.CommutativeCommandHandler;

/**
 * Handle the DepositFunds command.
 *
 * <p>Append strategy: commutative with lifecycle guard.
 * Create a {@code @Component} class implementing this interface.
 *
 * <pre>
 *   Query lifecycleModel = YourQueryPatterns.lifecycleModel(command.entityId());
 *   ProjectionResult&lt;YourState&gt; projection = eventStore.project(lifecycleModel, projector);
 *   if (!projection.state().isExisting()) throw new YourNotFoundException(command.entityId());
 *   AppendEvent appendEvent = AppendEvent.builder(type(YourEvent.class))
 *       .tag(TAG, command.entityId()).data(event).build();
 *   Query guard = YourQueryPatterns.lifecycleGuard(command.entityId());
 *   return CommandDecision.CommutativeGuarded.withLifecycleGuard(
 *       appendEvent, guard, projection.streamPosition());
 * </pre>
 */
public interface DepositFundsCommandHandler
        extends CommutativeCommandHandler<DepositFunds> {
}
```

**Pure commutative**:
```java
import com.crablet.command.CommutativeCommandHandler;

/**
 * Handle the RecordPageView command.
 *
 * <p>Append strategy: pure commutative — no lifecycle check.
 * Create a {@code @Component} class implementing this interface.
 *
 * <pre>
 *   AppendEvent appendEvent = AppendEvent.builder(type(PageViewRecorded.class))
 *       .tag(PAGE_ID, command.pageId()).data(event).build();
 *   return CommandDecision.Commutative.of(appendEvent);
 * </pre>
 */
public interface RecordPageViewCommandHandler
        extends CommutativeCommandHandler<RecordPageView> {
}
```

### Automation Handler Interface Template

The generator produces a metadata-only Java **interface** for each automation handler. Do not
declare `decide()`, do not add `@Component`, and do not include a structural implementation sketch
in Javadoc. Condition logic, view reads, deserialization, and emitted command mapping belong in the
user's separate `@Component` implementation.

Generate only default metadata methods:
- `getAutomationName()`
- `getEventTypes()`
- `getRequiredTags()` returning `Set.of()`

If `getRequiredTags()` is ever made non-empty, use string literals rather than tag constant classes.

```java
import com.crablet.automations.AutomationHandler;
import com.example.loan.domain.LoanApplicationSubmitted;

import java.util.Set;

import static com.crablet.eventstore.EventType.type;

/**
 * React to LoanApplicationSubmitted.
 *
 * <p>Create a {@code @Component} class implementing this interface and provide {@code decide()} logic.
 */
public interface SendWelcomeEmailAutomationHandler extends AutomationHandler {

    @Override
    default String getAutomationName() {
        return "send-welcome-email";
    }

    @Override
    default Set<String> getEventTypes() {
        return Set.of(type(LoanApplicationSubmitted.class));
    }

    @Override
    default Set<String> getRequiredTags() {
        return Set.of();
    }
}
```

### Outbox Publisher Interface Template

The generator produces a metadata-only Java **interface** for each outbox publisher. Do not declare
`publishBatch()` or `isHealthy()`, do not add `@Component`, and do not include a structural
implementation sketch in Javadoc. Client setup, auth, retry behavior, event iteration, and payload
mapping belong in the user's separate `@Component` implementation.

Generate only default metadata methods:
- `getName()`
- `getPreferredMode()`

`PublishMode` is a nested enum on `OutboxPublisher`. Once the interface extends `OutboxPublisher`,
`PublishMode` resolves as an inherited member type. Do not import `com.crablet.outbox.PublishMode`;
that standalone class does not exist.

```java
import com.crablet.outbox.OutboxPublisher;

/**
 * Publish loan events over HTTP webhook.
 *
 * <p>Create a {@code @Component} class implementing this interface and provide
 * {@code publishBatch()} and {@code isHealthy()} logic.
 */
public interface LoanNotificationsPublisher extends OutboxPublisher {

    @Override
    default String getName() {
        return "loan-notifications";
    }

    @Override
    default PublishMode getPreferredMode() {
        return PublishMode.INDIVIDUAL;
    }
}
```

### View Projector Template

Used by the views code generator. Implementation classes are normal `@Component` types (not
interfaces).

```java
@Component
public class YourViewProjector extends AbstractTypedViewProjector<YourEvent> {

    public YourViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "your_view_name";
    }

    @Override
    protected Class<YourEvent> getEventType() {
        return YourEvent.class;
    }

    @Override
    protected boolean handleEvent(YourEvent event, StoredEvent stored, JdbcTemplate jdbc) {
        return switch (event) {
            case EntityCreated created -> {
                jdbc.update(
                    "INSERT INTO your_view_table (id, field) VALUES (?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET field = EXCLUDED.field",
                    created.id(), created.field()
                );
                yield true;
            }
            case EntityUpdated updated -> {
                jdbc.update("UPDATE your_view_table SET field = ? WHERE id = ?",
                    updated.field(), updated.id());
                yield true;
            }
            default -> false;
        };
    }
}
```
