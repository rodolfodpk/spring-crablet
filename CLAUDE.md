# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Unit Test Template

```java
class YourHandlerUnitTest extends AbstractHandlerUnitTest {

    private YourCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new YourCommandHandler(/* ... */);
    }

    @Test
    void givenExistingEntity_whenExecutingCommand_thenEventGenerated() {
        // Given
        var created = EntityCreated.of("entity1", "data");
        given().event(type(EntityCreated.class), builder -> builder
            .data(created)
            .tag(ENTITY_ID, created.id())
        );

        // When
        YourCommand command = YourCommand.of("entity1", "param");
        List<Object> events = when(handler, command);

        // Then
        then(events, YourEvent.class, event -> {
            assertThat(event.entityId()).isEqualTo("entity1");
            assertThat(event.param()).isEqualTo("param");
        });
    }
}
```

## Build Commands

See [BUILD.md](docs/user/BUILD.md) for full details. Quick reference:

```bash
make install            # Full build with unit tests (recommended)
make install-all-tests  # Full build including integration tests
make test               # Run all tests
make clean              # Clean build artifacts
make start              # Run wallet-example-app

# Run specific module tests (after make install)
./mvnw test -pl <module-name>
./mvnw test -pl <module-name> -Dtest=ClassName
```

**Testing:**
- Tests use Testcontainers (Docker required, no external PostgreSQL needed)
- Unit tests: Fast in-memory tests (< 10ms)
- Integration tests: Real PostgreSQL via Testcontainers (100-500ms)

## Project Architecture

### Overview

Spring-Crablet is a lightweight Java 25 event sourcing framework built on the DCB (Dynamic Consistency Boundary) pattern. It provides event sourcing with optimistic concurrency control using streamPosition-based checks instead of distributed locks.

**Key Technologies:**
- Java 25 (records, sealed interfaces, virtual threads)
- Spring Boot 4.0
- PostgreSQL 17+ (MVCC, advisory locks, GIN indexes)
- Maven multi-module project

### Module Structure

```
crablet-eventstore (CORE - Required)
├── EventStore interface - Core event sourcing API
├── DCB implementation - Optimistic concurrency control
├── Query/StreamPosition/Tag - Event filtering and position tracking
└── StateProjector - State reconstruction from events

crablet-commands (Optional)
├── CommandHandler interface - Command handling pattern
├── CommandExecutor - Automatic handler discovery and orchestration
└── CommandDecision - Decision about which DCB pattern to apply

crablet-commands-web (Optional)
├── Generic HTTP command API — POST /api/commands
├── GET /api/commands — management endpoint listing exposed commands
├── Plain @RestController backed by CommandExecutor
├── CommandApiExposedCommands — allowlist of exposed command types
│   ├── fromPackages("com.myapp.wallet") — expose an entire vertical slice (recommended)
│   └── of(OpenWalletCommand.class, ...) — explicit class list
├── Optional springdoc integration — auto-generates oneOf Swagger schema when springdoc is present
└── Opt-in: include module + declare CommandApiExposedCommands bean

crablet-event-poller (Generic Infrastructure)
├── EventProcessor - Reusable polling infrastructure
├── EventSelection - Shared event matching contract
├── EventSelectionMatcher - In-memory counterpart to EventSelectionSqlBuilder (used by shared-fetch fan-out)
├── ProcessorRuntimeOverrides - Shared per-instance override contract
├── Leader election - PostgreSQL advisory locks
├── Progress tracking - Per-processor position tracking
├── Shared-fetch mode - Opt-in single-query-per-module architecture (requires schema V14)
└── Used by crablet-views, crablet-outbox, and crablet-automations

crablet-views (Optional)
├── ViewProjector interface - Materialized read models
├── Async event processing - Eventual consistency
├── ViewSubscription - Event selection + per-view poller config
└── Leader election per view processor

crablet-outbox (Optional)
├── OutboxPublisher interface - External event publishing
├── Transactional outbox pattern
├── One processor per (topic, publisher) pair
└── Multiple publishers support

crablet-automations (Optional)
├── AutomationHandler - Single public automation contract
├── Listen to events, execute commands automatically
├── Application reaction/orchestration after events
└── Leader election per automation processor

crablet-metrics-micrometer (Optional)
└── Auto-collects metrics from all modules

shared-examples-domain (Non-reactor, separate build)
├── Wallet domain - Complete working example
├── Course domain - Multi-entity constraints example
└── Used by all modules in test scope

wallet-example-app (Example application)
└── Complete Spring Boot application demonstrating framework usage

embabel-codegen (AI-first tooling, separate build)
├── CLI: init / plan / generate
├── MCP server: exposes embabel_init, embabel_plan, embabel_generate to Claude Code
├── AI agent pipeline: events → commands → views → automations → outbox → compile+repair
└── Uses Anthropic Java SDK with claude-sonnet-4-6

templates/crablet-app (Starter project template)
├── Pre-wired pom.xml, event-model.yaml skeleton, Flyway migration
├── Makefile: plan / generate / verify / check
└── .claude/settings.json wired for embabel-codegen MCP server
```

**Module dependencies:**
- `crablet-eventstore`: No dependencies on other modules
- `crablet-commands`: Depends on `crablet-eventstore`
- `crablet-commands-web`: Depends on `crablet-commands` + `spring-webmvc`
- `crablet-event-poller`: Depends on `crablet-eventstore`
- `crablet-views`: Depends on `crablet-eventstore` + `crablet-event-poller`
- `crablet-outbox`: Depends on `crablet-eventstore` + `crablet-event-poller`
- `crablet-automations`: Depends on `crablet-eventstore` + `crablet-event-poller` + `crablet-commands`

### Current Architecture Decisions

These decisions reflect the current repository state and should be treated as the preferred direction:

- `AutomationHandler` is the single public automation contract.
- Automations return `AutomationDecision` values through `AutomationHandler.decide()`.
- External event publication, including HTTP webhooks, belongs in `crablet-outbox`.
- Automations should decide what command/reaction should happen; they should not own outbox writes or depend on outbox internals. If an automation result needs reliable external publication, hand off the intent at an application/orchestration boundary and let `crablet-outbox` handle delivery.
- `AutomationSubscription` has been removed.
- `crablet-event-poller` now owns the shared matching and per-instance override abstractions:
  - `EventSelection`
  - `EventSelectionSqlBuilder`
  - `EventSelectionMatcher` — in-memory mirror of the SQL filter; used by shared-fetch to route events to each processor without extra DB queries
  - `ProcessorRuntimeOverrides`
  - `ProcessorRuntimeOverrideResolver`
- Generic `EventHandler<I>` no longer accepts a raw `DataSource`.
- Write-database access for views is owned by the `crablet-views` bridge (`ViewEventHandler` -> `ViewProjector`), not by the generic poller contract.
- Poller-backed modules should model configuration at two levels:
  - global module config with shared defaults
  - per-poller-instance config for one processor
- Examples:
  - views: global `ViewsConfig` + one `ViewSubscription` per view
  - automations: global `AutomationsConfig` + one `AutomationHandler` per automation
  - outbox: global `OutboxConfig` + one resolved processor per `(topic, publisher)` pair
- Poller deployment guidance:
  - prefer `1` application instance by default
  - use `2` instances at most for active/failover behavior
  - extra replicas do not increase throughput for the same processor set
- LISTEN/NOTIFY wakeup:
  - **NOTIFY** (`crablet-eventstore`) — always active; `pg_notify` fires on the write datasource after every append; no flag or extra config needed; Postgres discards silently when no one is listening.
  - **LISTEN** (`crablet-event-poller`) — opt-in via `crablet.event-poller.notifications.jdbc-url`; when set, a dedicated persistent connection LISTENs and wakes the poller immediately on each NOTIFY; when absent, pure scheduled polling is used.
  - The LISTEN `jdbc-url` **must be a direct connection** to Postgres — not a pooler URL. PgBouncer transaction mode, PgCat, and RDS Proxy do not support persistent LISTEN connections.
  - When wakeup is active, raise the polling interval to 30 s or more; scheduled polling becomes a safety net only.
- Shared-fetch mode (`*.shared-fetch.enabled=true`) is opt-in per module (views, automations, outbox). When enabled, one DB query per cycle fetches all events; `EventSelectionMatcher` routes them in-memory to each processor. Requires schema migration V14 (`module_scan_progress` + `processor_scan_progress` tables). Best combined with LISTEN wakeup and many processors on the same event stream.
- Web runtime coverage:
  - Both `crablet-commands-web` and `wallet-example-app` E2E use the default embedded Tomcat from `spring-boot-starter-web`; no explicit server override.
  - `crablet-commands-web` is server-agnostic at runtime — it depends only on `jakarta.servlet-api`.
  - Virtual-thread request dispatch is verified in both modules via a focused test-only endpoint, not by duplicating full API behavior tests.
- The root tutorial is now a tutorial series under `docs/user/tutorials/`, not one monolithic walkthrough.

### Build graph, examples, and `crablet-test-support`

- `shared-examples-domain` depends on `crablet-eventstore` (main) for real domain types; framework modules use it in **test** scope for realistic tests and shared scenarios.
- **`crablet-test-support`** holds shared test utilities (`InMemoryEventStore`, `AbstractHandlerUnitTest`, `AbstractCrabletTest`, `DCBTestHelpers`) so modules do not re-copy that code.
- **`shared-examples-domain`** and **`wallet-example-app`** are excluded from the reactor; **`make install`** (see `docs/user/BUILD.md`) applies the correct build order and stub JAR steps.

**When creating your own application:** depend on framework modules normally; your app does not need to live in this repo. Use your own domain in tests; framework tests keep using `shared-examples-domain` where needed.

### DCB (Dynamic Consistency Boundary) Pattern

Three append methods — pick based on operation type:

| Method | Use for | Example |
|--------|---------|---------|
| `appendNonCommutative` | Existing entities, order-matters | Withdraw, Transfer |
| `appendIdempotent` | Entity creation | OpenWallet, DefineCourse |
| `appendCommutative` | Order-independent, no lifecycle check | Analytics events |
| `appendCommutative` + `CommutativeGuarded` | Order-independent but entity must be active | Deposit, Credit |

**DCB Flow:** project state → validate → generate event → call the matching append method (atomic check + append).

Full explanation: `crablet-eventstore/docs/DCB_AND_CRABLET.md` and `crablet-eventstore/docs/COMMAND_PATTERNS.md`.

### Core Abstractions

**EventStore (crablet-eventstore/src/main/java/com/crablet/eventstore/EventStore.java):**
- `appendCommutative(events)` - Append without commutative-operation conflict (deposits, credits); used by both `Commutative` and `CommutativeGuarded` decisions — the guard check is performed before the append by `CommandExecutorImpl`
- `appendNonCommutative(events, decisionModel, streamPosition)` - Append with DCB stream-position check (withdrawals, transfers)
- `appendIdempotent(events, eventType, tagKey, tagValue)` - Append with duplicate-entity guard (entity creation)
- `project(query, streamPosition, stateType, projectors)` - State reconstruction
- `executeInTransaction(operation)` - Transaction wrapper

**CommandAuditStore (crablet-eventstore/src/main/java/com/crablet/eventstore/CommandAuditStore.java):**
- `storeCommand(json, type, txId)` - Command audit trail (implemented by `EventStoreImpl`; accessed via `instanceof` cast in `CommandExecutorImpl`)

**CommandExecutors (crablet-commands/src/main/java/com/crablet/command/CommandExecutors.java):**
- Public factory for creating `CommandExecutor`
- Preferred wiring entry point instead of instantiating `CommandExecutorImpl` directly

**AppendEvent vs StoredEvent:**
- `AppendEvent` - For writing (type, tags, data)
- `StoredEvent` - For reading (includes position, occurredAt, transactionId)

**Query (crablet-eventstore/src/main/java/com/crablet/eventstore/query/):**
- Defines which events are relevant for decisions
- Used for filtering, DCB conflict detection, state projection
- Built with `QueryBuilder`

**StreamPosition:**
- Captures event position (sequence number + timestamp + txId)
- Returned from `project()` via `ProjectionResult.streamPosition()`
- Core of DCB optimistic concurrency control

**StateProjector (crablet-eventstore/src/main/java/com/crablet/eventstore/projector/):**
- `transition(currentState, event, deserializer)` - Event-driven state transitions
- `getEventTypes()` - Filter which events to process
- `getInitialState()` - Starting state
- `StateProjector.exists(eventTypes...)` - Built-in factory for existence checks (returns `true` on first matching event)
- `EventStore.exists(query)` - Convenience shorthand: `boolean exists = eventStore.exists(query);`

**CommandHandler (crablet-commands/src/main/java/com/crablet/command/):**
- `handle(eventStore, command)` - Returns `CommandDecision`
- CommandExecutor automatically calls the correct append method based on the `CommandDecision` type
- All wrapped in single database transaction

### Testing Patterns

**Unit Testing (Fast, < 10ms):**
- Base class: `AbstractHandlerUnitTest` (in `crablet-test-support`)
- In-memory: `InMemoryEventStore` - no JSON serialization
- BDD-style: `given()`, `when()`, `then()`, `thenMultipleOrdered()`
- Pattern matching with sealed interfaces

**Integration Testing (Slower, 100-500ms):**
- Base class: `AbstractCrabletTest` (in `crablet-test-support`)
- Real PostgreSQL via Testcontainers
- Tests DCB concurrency, database constraints, idempotency

**Test utilities location:**
- `crablet-test-support/src/main/java/com/crablet/test/` - InMemoryEventStore, AbstractCrabletTest
- `crablet-test-support/src/main/java/com/crablet/eventstore/integration/` - DCBTestHelpers
- Maven dependency: `com.crablet:crablet-test-support` (test scope)

### Period Segmentation (@PeriodConfig)

Closing the books pattern for performance optimization with large event histories.

**Period types:** `MONTHLY`, `DAILY`, `HOURLY`, `YEARLY`, `NONE` (default)

**Framework provides:** `@PeriodConfig` annotation and `PeriodType` enum
**You implement:** Period helpers, resolvers, statement events, period-aware queries

**Documentation:** `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`

### View Projections (crablet-views)

Base class choices:
- `AbstractTypedViewProjector<E>` — recommended (sealed interfaces, pattern matching)
- `AbstractViewProjector` — simple events without sealed interfaces
- `ViewProjector` — maximum flexibility

Key rules: use `ON CONFLICT` upserts for idempotency; each batch committed atomically; tag-based filtering for subscriptions. See `crablet-views/README.md`.

### Event Publishing (crablet-outbox)

Implement `OutboxPublisher`: `publishBatch(events)`, `getName()`, `isHealthy()`, `getPreferredMode()`.
The generic poller owns fetch, progress, and retry/error tracking — publishers only handle the batch they receive.
See `crablet-outbox/README.md`.

### Leader Election

PostgreSQL advisory locks (`pg_try_advisory_lock`). Non-blocking — follower skips the cycle and retries next interval. Automatic failover: 5–30 seconds.

Key constraint: advisory locks are session-scoped. `crablet.event-poller.notifications.jdbc-url` **must be a direct Postgres connection** — not PgBouncer/PgCat/RDS Proxy. See `docs/user/LEADER_ELECTION.md`.

### DataSource Model

Crablet intentionally exposes two datasource roles:

- `WriteDataSource` for writes, progress tracking, and leader election
- `ReadDataSource` for read-only fetches that may be served by replicas

**DataSource ownership by layer** (important when extending framework classes):

| Layer | DataSource used | Why |
|-------|----------------|-----|
| Event fetching (views, automations, outbox) | `ReadDataSource` | Read-only; safe to serve from replica |
| View projection writes | `WriteDataSource` | Writes to the materialized view table |
| Progress tracking | `WriteDataSource` | Must be durable and consistent with writes |
| Leader election | `WriteDataSource` | Advisory locks are session-scoped; must stay on the write path |
| Command appends / EventStore | `WriteDataSource` | All writes go to primary |

**View projector DataSource:** `AbstractViewProjector` and `AbstractTypedViewProjector` inject `WriteDataSource` in their constructors. This is intentional — view projection writes must go to the primary. Do **not** inject `ReadDataSource` into a view projector constructor.

## Common Gotchas & Troubleshooting

See [docs/user/TROUBLESHOOTING.md](docs/user/TROUBLESHOOTING.md) for problem/solution pairs covering build issues, DCB pattern issues, testing issues, view projection issues, and event deserialization issues.

**Quick diagnostics:**
- `ConcurrencyException`: read the `Hint:`, check `matchingEventsCount`, verify decision-model tags match appended events.
- View projector silent failures: check logs for event type, position, `transaction_id`, tags; confirm `getEventTypes()` covers all variants.
- Enable `logging.level.com.crablet.eventstore=DEBUG` to see DCB query parameters and stream positions.

## Common Development Tasks

See [docs/user/tutorials/](docs/user/tutorials/) for step-by-step walkthroughs. Quick checklist:

**Adding a new command:** define command record → implement `CommandHandler<C>` → `@Component` → define events + decision model query → write unit + integration tests.

**Adding a new view:** Flyway migration for view table → implement `AbstractTypedViewProjector` → `@Component` → `handleEvent()` with upserts → integration tests.

**Running wallet example:** `make start` → API at `http://localhost:8080/api/`, Swagger at `/swagger-ui.html`.

## Important Coding Patterns & Conventions

### Java Style

- **Never use fully qualified class names in code.** Always add import statements at the top of the file. Fully qualified names (e.g., `com.crablet.eventstore.StoredEvent event` inline) are forbidden; use imports instead.
- **Never call `Instant.now()` directly.** Always inject `ClockProvider` and call `clockProvider.now()`. This is mandatory for deterministic tests — any direct `Instant.now()` call makes time-sensitive logic untestable.

### Naming Conventions

**Tags** (snake_case):
- Use constants from domain-specific tag classes (e.g., `WalletTags.WALLET_ID`, `WalletTags.DEPOSIT_ID`)
- Format: `wallet_id`, `deposit_id`, `withdrawal_id`, `transfer_id`, `from_wallet_id`, `to_wallet_id`
- Period tags: `year`, `month`, `day`, `hour` (for closing the books pattern)
- **Tag keys are automatically normalized to lowercase** at construction time (`Tag` compact constructor, `Locale.ROOT`). Passing `"WALLET_ID"` is equivalent to `"wallet_id"`. Tag **values** are case-sensitive and never modified.

**Event Types** (PascalCase):
- Use `EventType.type(EventClass.class)` pattern for type-safe event type names
- Examples: `type(WalletOpened.class)`, `type(DepositMade.class)`, `type(WithdrawalMade.class)`

**Event Classes**:
- Use sealed interfaces for pattern matching: `sealed interface WalletEvent permits WalletOpened, DepositMade, ...`
- Records for event data: `record DepositMade(String depositId, String walletId, int amount, ...) implements WalletEvent`

### Command Validation Pattern (YAVI)

**Pattern:** Commands validate themselves at construction (YAVI); handlers can assume commands are
valid. See existing commands in `shared-examples-domain` for the validator wiring pattern.

### Query Building Patterns

**Pattern 1: Events with shared tag**
```java
QueryBuilder.builder()
    .events(type(WalletOpened.class), type(DepositMade.class), type(WithdrawalMade.class))
    .tag(WALLET_ID, walletId)
    .build();
```

**Pattern 2: Single event type with specific tag**
```java
QueryBuilder.builder()
    .event(type(MoneyTransferred.class), FROM_WALLET_ID, walletId)
    .build();
```

**Pattern 3: Events with multiple tags (all must match)**
```java
QueryBuilder.builder()
    .matching(
        new String[]{type(WalletStatementOpened.class)},
        QueryBuilder.tag(WALLET_ID, walletId),
        QueryBuilder.tag(YEAR, String.valueOf(year)),
        QueryBuilder.tag(MONTH, String.valueOf(month))
    )
    .build();
```

**Reusable Query Patterns**: Create domain-specific query pattern classes (e.g., `WalletQueryPatterns.singleWalletDecisionModel(walletId)`)

### Database Schema Key Points

**events table:**
- `position` (BIGSERIAL) - Global sequence number, primary key
- `transaction_id` (xid8) - PostgreSQL transaction ID for ordering
- `tags` (TEXT[]) - Array of tags with GIN index for fast filtering
- `data` (JSON) - Event payload
- `type` (VARCHAR 64) - Event type name
- `occurred_at` (TIMESTAMP WITH TIME ZONE) - Event timestamp

**Key indexes:**
- `idx_events_tags` (GIN) - Fast tag-based filtering
- `idx_events_type_position` - Optimized for DCB query pattern
- `idx_events_type_tags_gin` - Optimized for idempotency checks

**PostgreSQL functions:**
- `append_events_if()` - Atomic DCB check and append (uses advisory locks for idempotency)
- `append_events_batch()` - Batch insert using UNNEST

**commands table:**
- Stores command audit trail with transaction_id linking to events

### Spring Boot Auto-Configuration

Modules use Spring Boot auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
- `crablet-views`: Auto-configures view processing infrastructure
- `crablet-outbox`: Auto-configures outbox processing infrastructure
- Add `@SpringBootApplication` and the framework auto-wires itself

## Important Architectural Principles

1. **Idempotency**: All operations must be idempotent (views, outbox, command handlers with idempotency checks)

2. **Atomicity**: Command handler + events + audit = single transaction

3. **StreamPosition-based Concurrency**: Capture stream position from projection, use in AppendCondition for DCB checks

4. **Event Immutability**: Events are immutable once appended

5. **At-Least-Once Processing**: Views and outbox process events at-least-once (progress updates in separate transaction)

6. **Leader Election**: Only one instance processes per view/outbox scheduler

7. **Read/Write Separation**: Commands via EventStore (write model), queries via views (read model, eventual consistency)

8. **Tag-based Filtering**: Tags are the primary mechanism for event filtering and decision models

9. **Type Safety**: Use `EventType.type(Class)` for event types and sealed interfaces for pattern matching

## Developer experience (diagnostics)

- **Concurrency / idempotency:** `ConcurrencyException` and related errors include violation codes, matching event counts, and short `Hint:` text. Use `getDiagnostics()` for structured logs.
- **DCB debugging:** `logging.level.com.crablet.eventstore=DEBUG` logs query parameters, stream position, and tags used in append checks.
- **Views:** projector failures log event type, position, `transaction_id`, tags, and view name; confirm `getEventTypes()` and SQL match every variant you emit.

## Performance Considerations

See [docs/user/PERFORMANCE.md](docs/user/PERFORMANCE.md) for full details on read replicas, database indexes, batch processing, connection pooling, and closing the books pattern.

## Documentation Quick Links

- **Doc layout:** `docs/README.md` — **user** (`docs/user/`) vs **dev** (`docs/dev/`)
- **User docs** (applications on Crablet): start at `docs/user/README.md`
- **Framework development** (maintainers — design notes, plans, reviews): start at `docs/dev/README.md`
- Maintainer notes and exploratory spikes (non-binding): `docs/dev/notes-future.md`
- Build instructions: `docs/user/BUILD.md`
- Configuration reference: `docs/user/CONFIGURATION.md`
- Troubleshooting: `docs/user/TROUBLESHOOTING.md`
- Performance: `docs/user/PERFORMANCE.md`
- EventStore README: `crablet-eventstore/README.md`
- Command framework: `crablet-commands/README.md`
- Event processor: `crablet-event-poller/README.md`
- Outbox: `crablet-outbox/README.md`
- Views: `crablet-views/README.md`
- Testing guide: `crablet-eventstore/TESTING.md`
- DCB explained: `crablet-eventstore/docs/DCB_AND_CRABLET.md`
- Command patterns: `crablet-eventstore/docs/COMMAND_PATTERNS.md`
- Closing books: `crablet-eventstore/docs/CLOSING_BOOKS_PATTERN.md`
- Leader election: `docs/user/LEADER_ELECTION.md`
- Read replicas: `crablet-eventstore/docs/READ_REPLICAS.md`
- AI-first codegen: `embabel-codegen/README.md` (CLI, MCP server, agent pipeline, error recovery)
- Starter template: `templates/README.md`, `templates/crablet-app/README.md`
- AI workflow: `docs/user/ai-tooling/AI_FIRST_WORKFLOW.md`, `docs/user/ai-tooling/FEATURE_SLICE_WORKFLOW.md`
- Event model format: `docs/user/ai-tooling/EVENT_MODEL_FORMAT.md` (includes shared schema / $ref composition)
- Upgrade guide: `docs/user/UPGRADE.md` (breaking changes and migration steps)
- Observability: `docs/user/OBSERVABILITY.md` (entry point — Micrometer setup, metrics reference, Grafana, PromQL)
- Metrics deep dives: `crablet-eventstore/docs/METRICS.md`, `crablet-outbox/docs/OUTBOX_METRICS.md`
- Observability stack: `observability/README.md` (Prometheus + Grafana Docker Compose)

## Key Package Locations

**Core packages:**
- EventStore: `crablet-eventstore/src/main/java/com/crablet/eventstore/`
- Command framework: `crablet-commands/src/main/java/com/crablet/command/`
- Views: `crablet-views/src/main/java/com/crablet/views/`
- Outbox: `crablet-outbox/src/main/java/com/crablet/outbox/`

**Example domains:**
- Wallet: `shared-examples-domain/src/main/java/com/crablet/examples/wallet/`
- Course: `shared-examples-domain/src/main/java/com/crablet/examples/course/`

**Test utilities:**
- `crablet-test-support/src/main/java/com/crablet/test/` - InMemoryEventStore, AbstractCrabletTest
- `crablet-test-support/src/main/java/com/crablet/eventstore/integration/` - DCBTestHelpers
- Maven: `com.crablet:crablet-test-support` (test scope)
