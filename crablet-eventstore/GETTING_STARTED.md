# Getting Started With Crablet

This guide is the practical path for a first integration.

Canonical compile fixture:
[docs-samples/src/main/java/com/crablet/docs/samples/tutorial/GettingStartedWalletSample.java](../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/GettingStartedWalletSample.java)

Recommended first adoption path:

- start with `crablet-eventstore` and `crablet-commands`
- keep the first milestone command-side only
- use the wallet domain as a reference shape
- add views, automations, or outbox only after the write flow is working

If you want the fastest runnable path instead of a library integration walkthrough, start with [../docs/QUICKSTART.md](../docs/QUICKSTART.md) and the [../wallet-example-app/README.md](../wallet-example-app/README.md).

## Dependencies

Add the core modules:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-commands</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>runtime</scope>
</dependency>
```

Use `crablet-commands` when you want handler discovery and transactional command execution. If you want to start with direct `EventStore` usage, `crablet-eventstore` can stand alone.

## Step 1: Set Up The Database

See [SCHEMA.md](SCHEMA.md) for the event store schema details.

For now, treat the SQL in the repository as the source migration content and publish those migrations through your own Flyway or Liquibase setup. The long-term goal should be a first-class published migration path, but the important point is: do not treat test resources as the integration contract.

Minimal Spring datasource config:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.flyway.enabled=true
```

## Step 2: Define Events

```java
package com.example.wallet.events;

public sealed interface WalletEvent permits WalletOpened, DepositMade, WithdrawalMade {}

public record WalletOpened(String walletId, String owner, int initialBalance) implements WalletEvent {}
public record DepositMade(String depositId, String walletId, int amount, int newBalance) implements WalletEvent {}
public record WithdrawalMade(String withdrawalId, String walletId, int amount, int newBalance)
        implements WalletEvent {}
```

Use `EventType.type(...)` when you want the framework event name derived from the class:

```java
import static com.crablet.eventstore.EventType.type;
```

## Step 3: Define Minimal Decision State

Command handlers should project only the state they need for business decisions.

```java
package com.example.wallet.projections;

public record WalletBalanceState(String walletId, int balance, boolean exists) {

    public boolean isExisting() {
        return exists;
    }

    public boolean hasSufficientFunds(int amount) {
        return exists && balance >= amount;
    }
}
```

## Step 4: Create A State Projector

```java
package com.example.wallet.projections;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.example.wallet.events.DepositMade;
import com.example.wallet.events.WalletEvent;
import com.example.wallet.events.WalletOpened;
import com.example.wallet.events.WithdrawalMade;

import java.util.List;

import static com.crablet.eventstore.EventType.type;

public class WalletBalanceStateProjector implements StateProjector<WalletBalanceState> {

    @Override
    public List<String> getEventTypes() {
        return List.of(
                type(WalletOpened.class),
                type(DepositMade.class),
                type(WithdrawalMade.class)
        );
    }

    @Override
    public WalletBalanceState getInitialState() {
        return new WalletBalanceState("", 0, false);
    }

    @Override
    public WalletBalanceState transition(
            WalletBalanceState currentState,
            StoredEvent event,
            EventDeserializer deserializer) {
        WalletEvent walletEvent = deserializer.deserialize(event, WalletEvent.class);

        return switch (walletEvent) {
            case WalletOpened opened -> new WalletBalanceState(
                    opened.walletId(),
                    opened.initialBalance(),
                    true
            );
            case DepositMade deposit -> new WalletBalanceState(
                    deposit.walletId(),
                    deposit.newBalance(),
                    true
            );
            case WithdrawalMade withdrawal -> new WalletBalanceState(
                    withdrawal.walletId(),
                    withdrawal.newBalance(),
                    true
            );
        };
    }
}
```

## Step 5: Define A Query Pattern

Keep DCB decision models in reusable query helpers instead of rebuilding queries inside every handler.

```java
package com.example.wallet;

import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.QueryBuilder;
import com.example.wallet.events.DepositMade;
import com.example.wallet.events.WalletOpened;
import com.example.wallet.events.WithdrawalMade;

import static com.crablet.eventstore.EventType.type;

public final class WalletQueryPatterns {

    private WalletQueryPatterns() {
    }

    public static Query singleWalletDecisionModel(String walletId) {
        return QueryBuilder.builder()
                .events(
                        type(WalletOpened.class),
                        type(DepositMade.class),
                        type(WithdrawalMade.class)
                )
                .tag("wallet_id", walletId)
                .build();
    }
}
```

## Step 6: Create A Command Interface

Current command registration uses a Jackson polymorphic hierarchy:

```java
package com.example.wallet.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet"),
        @JsonSubTypes.Type(value = DepositCommand.class, name = "deposit"),
        @JsonSubTypes.Type(value = WithdrawCommand.class, name = "withdraw")
})
public interface WalletCommand {
    String getWalletId();
}
```

Each concrete command implements that interface:

```java
public record WithdrawCommand(String walletId, String withdrawalId, int amount) implements WalletCommand {
}
```

## Step 7: Implement A Command Handler

This is the typical non-commutative flow:

1. load the decision model
2. project minimal state
3. validate business rules
4. create events
5. return `CommandDecision.NonCommutative`

```java
package com.example.wallet.commands;

import com.crablet.command.CommandDecision;
import com.crablet.command.NonCommutativeCommandHandler;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.example.wallet.WalletQueryPatterns;
import com.example.wallet.events.WithdrawalMade;
import com.example.wallet.projections.WalletBalanceState;
import com.example.wallet.projections.WalletBalanceStateProjector;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;

@Component
public class WithdrawCommandHandler implements NonCommutativeCommandHandler<WithdrawCommand> {

    private final WalletBalanceStateProjector projector = new WalletBalanceStateProjector();

    @Override
    public CommandDecision.NonCommutative decide(EventStore eventStore, WithdrawCommand command) {
        Query decisionModel = WalletQueryPatterns.singleWalletDecisionModel(command.walletId());
        ProjectionResult<WalletBalanceState> projection = eventStore.project(decisionModel, projector);

        if (!projection.state().isExisting()) {
            throw new IllegalStateException("Wallet not found: " + command.walletId());
        }
        if (!projection.state().hasSufficientFunds(command.amount())) {
            throw new IllegalStateException("Insufficient funds: " + command.walletId());
        }

        int newBalance = projection.state().balance() - command.amount();

        WithdrawalMade withdrawal = new WithdrawalMade(
                command.withdrawalId(),
                command.walletId(),
                command.amount(),
                newBalance
        );

        AppendEvent event = AppendEvent.builder(type(WithdrawalMade.class))
                .tag("wallet_id", command.walletId())
                .tag("withdrawal_id", command.withdrawalId())
                .data(withdrawal)
                .build();

        return CommandDecision.NonCommutative.of(
                event,
                decisionModel,
                projection.streamPosition()
        );
    }
}
```

## Step 8: Execute Commands

```java
package com.example.wallet.service;

import com.crablet.command.CommandExecutor;
import com.crablet.command.ExecutionResult;
import com.example.wallet.commands.WithdrawCommand;
import org.springframework.stereotype.Service;

@Service
public class WalletService {

    private final CommandExecutor commandExecutor;

    public WalletService(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public ExecutionResult withdraw(String walletId, String withdrawalId, int amount) {
        return commandExecutor.execute(new WithdrawCommand(walletId, withdrawalId, amount));
    }
}
```

## What To Add Next

Once the command-side flow works:

- add views if you want materialized read models
- add outbox if you need reliable publication to external systems
- add automations for event-driven side effects

If you add any of those poller-backed modules, default to **one application instance per cluster**.

## Next Reading

- Command-side adoption path: [../docs/COMMANDS_FIRST_ADOPTION.md](../docs/COMMANDS_FIRST_ADOPTION.md)
- Production topology: [../docs/DEPLOYMENT_TOPOLOGY.md](../docs/DEPLOYMENT_TOPOLOGY.md)
- EventStore details: [README.md](README.md)
- Wallet example app: [../wallet-example-app/README.md](../wallet-example-app/README.md)
