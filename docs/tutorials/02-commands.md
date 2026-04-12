# Part 2: Commands

This tutorial introduces `crablet-commands`.

Canonical compile fixture:
[docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial02CommandsSample.java](../../docs-samples/src/main/java/com/crablet/docs/samples/tutorial/Tutorial02CommandsSample.java)

## Why This Part Exists

You can call `EventStore` directly, but most applications want a stable write path:

- command in
- business decision
- events out
- one transactional execution path

That is what `crablet-commands` gives you.

Skip this part if you already know why command handlers exist and only want to focus on DCB-specific consistency rules.

You will learn:

- how command handlers wrap event-store writes
- how Crablet maps command types to different append semantics
- how to keep command logic transactional and explicit

Assume this import in the snippets below:

```java
import static com.crablet.eventstore.EventType.type;
```

## Why Use Commands

You can call `EventStore` directly, but `CommandHandler` gives you:

- automatic handler discovery
- one transactional execution path
- a clear separation between command intent and event persistence

## Example

Current command registration uses a Jackson polymorphic command interface:

```java
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "commandType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OpenWalletCommand.class, name = "open_wallet")
})
public interface WalletCommand {
}
```

```java
public record OpenWalletCommand(
    String walletId,
    String owner,
    int initialBalance
) implements WalletCommand {}

@Component
public class OpenWalletCommandHandler implements IdempotentCommandHandler<OpenWalletCommand> {

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, OpenWalletCommand command) {
        WalletOpened event = new WalletOpened(
            command.walletId(), command.owner(), command.initialBalance()
        );

        AppendEvent appendEvent = AppendEvent.builder(type(WalletOpened.class))
            .tag("wallet_id", command.walletId())
            .data(event)
            .build();

        return CommandDecision.Idempotent.of(
            appendEvent,
            type(WalletOpened.class),
            "wallet_id",
            command.walletId()
        );
    }
}
```

Execute it through `CommandExecutor`:

```java
ExecutionResult result = commandExecutor.execute(
    new OpenWalletCommand("wallet-1", "alice", 100)
);
```

The key point is not the syntax. The key point is that the handler returns a `CommandDecision`, and `CommandExecutor` turns that into the correct append behavior inside one transaction.

## What To Choose

- `CommutativeCommandHandler`: order does not matter
- `IdempotentCommandHandler`: entity creation or duplicate-prevention
- `NonCommutativeCommandHandler`: state-dependent decisions

## Checkpoint

After this part, you should understand why Crablet pushes application writes through commands instead of ad hoc event appends.

Expected result:

- you define one handler per command type
- `CommandExecutor` becomes the single entry point for writes
- the handler returns a `CommandDecision` that makes concurrency intent explicit

## Next

Continue with [Part 3: DCB Consistency Boundaries](03-dcb-consistency-boundaries.md).
