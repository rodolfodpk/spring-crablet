# Shared Examples Domain

Reusable example domains used by all framework modules in test scope. Provides realistic domain implementations for testing framework features — not mocks, real business logic.

## Overview

`shared-examples-domain` contains three complete example domains:

| Domain | Purpose |
|--------|---------|
| **Wallet** | Financial operations — DCB patterns, period segmentation, all three append condition types |
| **Course** | Multi-entity constraints — cross-entity consistency (course capacity + student subscription limit) |
| **Notification** | Reaction example — triggered by `WalletOpened`, records `WelcomeNotificationSent` |

All framework modules (`crablet-eventstore`, `crablet-commands`, `crablet-views`, etc.) depend on this module in test scope so their tests run against the same real domain logic — consistent, realistic, living documentation.

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>shared-examples-domain</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Wallet Domain

The primary example domain. Demonstrates all DCB patterns.

### Commands

| Command | DCB Pattern | Description |
|---------|-------------|-------------|
| `OpenWalletCommand` | Idempotency check | Creates a wallet — fails if wallet already exists |
| `DepositCommand` | Empty condition | Commutative operation — order independent |
| `WithdrawCommand` | Cursor-based check | Non-commutative — enforces balance check |
| `TransferMoneyCommand` | Cursor-based check | Cross-wallet — updates two wallets atomically |

### Events (sealed interface)

```java
sealed interface WalletEvent permits
    WalletOpened, DepositMade, WithdrawalMade, MoneyTransferred,
    WalletStatementOpened, WalletStatementClosed {}
```

### Tags

```java
WalletTags.WALLET_ID       // "wallet_id"
WalletTags.DEPOSIT_ID      // "deposit_id"
WalletTags.WITHDRAWAL_ID   // "withdrawal_id"
WalletTags.TRANSFER_ID     // "transfer_id"
WalletTags.FROM_WALLET_ID  // "from_wallet_id"
WalletTags.TO_WALLET_ID    // "to_wallet_id"
```

### Query Patterns

```java
// Single wallet decision model
WalletQueryPatterns.singleWalletDecisionModel(walletId);

// Transfer decision model (spans two wallets)
WalletQueryPatterns.transferDecisionModel(fromWalletId, toWalletId);
```

### Period Segmentation (Closing the Books)

The `WalletCommand` interface is annotated with `@PeriodConfig(PeriodType.MONTHLY)`. The wallet domain provides:

- `WalletPeriodHelper` — projects current period balance, ensures active period
- `WalletStatementPeriodResolver` — resolves period boundaries from events
- `PeriodConfigurationProvider` — reads `@PeriodConfig` annotation at runtime

See [Closing the Books Pattern](../crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md) for complete documentation.

## Course Domain

Demonstrates multi-entity consistency constraints spanning more than one aggregate.

### Commands

- `DefineCourseCommand` — creates a course with a capacity limit
- `ChangeCourseCapacityCommand` — changes course capacity
- `SubscribeStudentToCourseCommand` — subscribes a student, enforcing:
  - Course must exist
  - Course must not be full
  - Student must not already be subscribed
  - Student must not exceed their personal subscription limit

### Events (sealed interface)

```java
sealed interface CourseEvent permits
    CourseDefined, CourseCapacityChanged, StudentSubscribedToCourse {}
```

### Cross-Entity DCB Example

`SubscribeStudentToCourseCommand` spans both `course_id` and `student_id` tags, building a decision model that reads events from both entities in a single query. This is the core DCB multi-entity consistency pattern.

## Notification Domain

Demonstrates the reaction → command → event chain triggered by the wallet domain.

### Flow

```
WalletOpened event
    → WalletOpenedReaction (in wallet-example-app)
    → SendWelcomeNotificationCommand
    → SendWelcomeNotificationCommandHandler
    → WelcomeNotificationSent event (with idempotency check)
```

### Command Interface

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SendWelcomeNotificationCommand.class, name = "send_welcome_notification")
})
public interface NotificationCommand {}
```

### Handler

`SendWelcomeNotificationCommandHandler` logs a welcome message and appends `WelcomeNotificationSent` with an idempotency check on `(WelcomeNotificationSent, wallet_id, walletId)` — safe to re-execute if the reaction fires more than once.

## Build Notes

`shared-examples-domain` lives outside the reactor because it creates a build-time cycle:

```
shared-examples-domain → crablet-eventstore (main scope)
crablet-eventstore     → shared-examples-domain (test scope)   ← cycle
```

The Makefile resolves this by building `shared-examples-domain` after the framework modules (skip tests), then running the full reactor with all tests.

See [BUILD.md](../BUILD.md) for details.

## See Also

- [Command README](../crablet-commands/README.md) — Command framework
- [EventStore TESTING.md](../crablet-eventstore/TESTING.md) — Testing strategy
- [DCB Explained](../crablet-eventstore/docs/DCB_AND_CRABLET.md) — DCB pattern details
- [Command Patterns](../crablet-eventstore/docs/COMMAND_PATTERNS.md) — All three DCB append condition types
