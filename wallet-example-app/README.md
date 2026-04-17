# Wallet Example App

The wallet example app is the recommended learning entry point for Crablet.

It demonstrates the full stack in the simplest teaching topology:

- one application instance
- command handling
- view projections
- automations
- outbox-style background processing
- REST API with OpenAPI documentation

If you are new to Crablet, start here before reading the deeper DCB material.

## Overview

This application demonstrates a wallet management system with:
- **Commands**: Open wallet, deposit, withdraw, transfer money
- **Events**: WalletOpened, DepositMade, WithdrawalMade, MoneyTransferred
- **Views**: Four materialized views for different use cases:
  - `wallet_balance_view` - Fast balance lookups
  - `wallet_transaction_view` - Transaction history
  - `wallet_summary_view` - Aggregated statistics
  - `wallet_statement_view` - Period-based statement tracking
- **Automations**: Event-driven follow-up work — when a wallet is opened, send a welcome notification
- **View Management**: REST API for monitoring and controlling view projections

## Architecture

```
┌─────────────┐
│   REST API  │  ← OpenAPI documented endpoints
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Command     │  ← Command handlers (DCB pattern)
│  Handlers    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Event Store │  ← Events appended with DCB conditions
└──────┬──────┘
       │
       ├─────────────────┐
       │                 │
       ▼                 ▼
┌─────────────┐   ┌─────────────┐
│   Views     │   │   Views     │  ← Asynchronous projections
│  Processor  │   │  Processor  │
└──────┬──────┘   └──────┬──────┘
       │                 │
       ▼                 ▼
┌─────────────┐   ┌─────────────┐
│  Balance    │   │ Transaction │  ← Materialized views
│    View     │   │    View     │
└─────────────┘   └─────────────┘
```

## Quick Start

This application is intentionally a **single-instance learning setup**. That is the recommended default whenever poller-backed modules are enabled.

### Prerequisites

- Java 25+
- PostgreSQL 17+
- Maven (or use `./mvnw`)

### Setup

1. **Create database:**
```bash
createdb wallet_db
```

2. **Configure database connection** in `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

3. **Build and run:**

**Option 1: Using Makefile (from project root)**
```bash
# Build all library modules first
make install

# Run the application
make start
# or
make wallet-dev
```

**Option 2: Standalone build (from wallet-example-app directory)**
```bash
# First, ensure library modules are installed to local Maven repository
# From project root:
make install

# Then build and run the example app
cd wallet-example-app
../mvnw install
../mvnw spring-boot:run
```

The application will:
- Run Flyway migrations (creates Crablet tables + view tables)
- Start the REST API on port 8080
- Start view processors (asynchronous projections)

### First 3 Requests To Try

Open a wallet:

```bash
curl -X POST http://localhost:8080/api/wallets \
  -H 'Content-Type: application/json' \
  -d '{
    "walletId": "wallet-123",
    "owner": "Jane Doe",
    "initialBalance": 100
  }'
```

Deposit money:

```bash
curl -X POST http://localhost:8080/api/wallets/wallet-123/deposits \
  -H 'Content-Type: application/json' \
  -d '{
    "depositId": "deposit-001",
    "amount": 25,
    "description": "Initial top-up"
  }'
```

Read the projected balance:

```bash
curl http://localhost:8080/api/wallets/wallet-123
```

Expected outcome:

- command-side writes succeed immediately
- the read model catches up asynchronously
- you see the basic Crablet write-to-read flow without extra deployment complexity

### Access API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## API Endpoints

### Commands (Write Operations)

#### Generic Command API

All wallet commands are also accessible via the generic command endpoint provided by `crablet-commands-web`:

```bash
POST /api/commands
Content-Type: application/json
X-Correlation-ID: <optional UUID>   # auto-generated when absent

{
  "commandType": "open_wallet",
  "walletId": "wallet-123",
  "owner": "John Doe",
  "initialBalance": 100
}
```

The `commandType` field selects the handler. Only commands declared in the `CommandApiExposedCommands` bean are reachable; all others return `404`.

The optional `X-Correlation-ID` request header ties all events produced by the same HTTP request together. If omitted, a random UUID is generated automatically. The correlation ID is stored on every appended event and is readable via `StoredEvent.correlationId()`.

#### Open Wallet
```bash
POST /api/wallets
Content-Type: application/json

{
  "walletId": "wallet-123",
  "owner": "John Doe",
  "initialBalance": 100
}
```

#### Deposit Money
```bash
POST /api/wallets/{walletId}/deposits
Content-Type: application/json

{
  "depositId": "deposit-456",
  "amount": 50,
  "description": "Salary payment"
}
```

#### Withdraw Money
```bash
POST /api/wallets/{walletId}/withdrawals
Content-Type: application/json

{
  "withdrawalId": "withdrawal-789",
  "amount": 25,
  "description": "Purchase at store"
}
```

#### Transfer Money
```bash
POST /api/wallets/transfers
Content-Type: application/json

{
  "transferId": "transfer-101",
  "fromWalletId": "wallet-123",
  "toWalletId": "wallet-456",
  "amount": 30,
  "description": "Payment for services"
}
```

### Queries (Read Operations)

#### Get Wallet Balance
```bash
GET /api/wallets/{walletId}
```

#### Get Transaction History
```bash
GET /api/wallets/{walletId}/transactions?page=0&size=20
```

#### Get Wallet Summary
```bash
GET /api/wallets/{walletId}/summary
```

### Outbox Management

```bash
GET  /api/outbox/status
GET  /api/outbox/{topic}/publishers/{publisher}/status
GET  /api/outbox/{topic}/publishers/{publisher}/details
GET  /api/outbox/{topic}/publishers/{publisher}/lag
POST /api/outbox/{topic}/publishers/{publisher}/pause
POST /api/outbox/{topic}/publishers/{publisher}/resume
POST /api/outbox/{topic}/publishers/{publisher}/reset
```

Example:
```bash
curl http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/status
curl -X POST http://localhost:8080/api/outbox/wallet-events/publishers/LogPublisher/pause
```

### View Management

The application provides a REST API for managing and monitoring view projections:

#### Get View Status
```bash
GET /api/views/{viewName}/status
```

**Response:**
```json
{
  "viewName": "wallet-balance-view",
  "status": "ACTIVE",
  "lag": 0
}
```

#### Get All View Statuses
```bash
GET /api/views/status
```

**Response:**
```json
{
  "wallet-balance-view": "ACTIVE",
  "wallet-transaction-view": "ACTIVE",
  "wallet-summary-view": "PAUSED",
  "wallet-statement-view": "ACTIVE"
}
```

#### Pause View Processing
```bash
POST /api/views/{viewName}/pause
```

**Response:**
```json
{
  "viewName": "wallet-balance-view",
  "status": "PAUSED",
  "message": "View projection paused successfully"
}
```

#### Resume View Processing
```bash
POST /api/views/{viewName}/resume
```

#### Reset Failed View
```bash
POST /api/views/{viewName}/reset
```

#### Get Detailed View Progress
```bash
GET /api/views/{viewName}/details
```

**Response:**
```json
{
  "viewName": "wallet-balance-view",
  "instanceId": "instance-123",
  "status": "ACTIVE",
  "lastPosition": 1000,
  "errorCount": 0,
  "lastError": null,
  "lastErrorAt": null,
  "lastUpdatedAt": "2026-01-13T18:00:00Z",
  "createdAt": "2026-01-13T17:00:00Z"
}
```

#### Get All View Progress Details
```bash
GET /api/views/details
```

#### Get View Lag
```bash
GET /api/views/{viewName}/lag
```

**Response:**
```json
{
  "viewName": "wallet-balance-view",
  "lag": 0
}
```

**Example Usage:**
```bash
# Check status of all views
curl http://localhost:8080/api/views/status

# Get detailed progress for a specific view
curl http://localhost:8080/api/views/wallet-balance-view/details

# Pause a view for maintenance
curl -X POST http://localhost:8080/api/views/wallet-balance-view/pause

# Resume processing
curl -X POST http://localhost:8080/api/views/wallet-balance-view/resume
```

## View Projections

The application includes four view projections:

### 1. Wallet Balance View (`wallet-balance-view`)

**Table**: `wallet_balance_view`
- **Purpose**: Fast balance lookups for API queries
- **Events**: WalletOpened, DepositMade, WithdrawalMade, MoneyTransferred
- **Idempotency**: Uses `new_balance` from events (idempotent by design)

### 2. Wallet Transaction View (`wallet-transaction-view`)

**Table**: `wallet_transaction_view`
- **Purpose**: Transaction history for audit and reporting
- **Events**: DepositMade, WithdrawalMade, MoneyTransferred
- **Idempotency**: Uses `(transaction_id, event_position)` as unique constraint

### 3. Wallet Summary View (`wallet-summary-view`)

**Table**: `wallet_summary_view`
- **Purpose**: Aggregated statistics for dashboards
- **Events**: All wallet events
- **Idempotency**: Uses `wallet_id` as PK with upsert

### 4. Wallet Statement View (`wallet-statement-view`)

**Table**: `wallet_statement_view`
- **Purpose**: Period-based statement tracking with totals for reconciliation
- **Events**: WalletStatementOpened, WalletStatementClosed, DepositMade, WithdrawalMade, MoneyTransferred
- **Idempotency**: Uses `statement_transactions` junction table with `(statement_id, event_position)` PK
- **Features**:
  - Tracks statement periods (year, month, day, hour) for closing the books pattern
  - Maintains opening and closing balances per period
  - Aggregates period totals (deposits, withdrawals, transfers in/out, transaction count)
  - Supports reconciliation by providing period activity summaries

## Automations

The example automation class, `WalletOpenedAutomation`, listens for `WalletOpened` events and executes a `SendWelcomeNotificationCommand`, which logs a welcome message and records a `WelcomeNotificationSent` event.

This demonstrates the full automation → command → event chain:
```
WalletOpened event
    → WalletOpenedAutomation
    → SendWelcomeNotificationCommand
    → WelcomeNotificationSent (with idempotency check)
```

In the current example, `WalletOpenedAutomation` is an in-process `AutomationHandler`, so it does not override `getWebhookUrl()`.

## Outbox

The outbox processor publishes every appended event to a registered `OutboxPublisher`.
In this app, `LogPublisher` (from `crablet-outbox`) forwards each event to SLF4J —
useful for observing what is forwarded and as a template for real integrations.

```
Event appended to event store
    → Outbox processor polls event store
    → LogPublisher.publishBatch([WalletOpened, ...])
    → SLF4J log line per event
```

To use a real integration (Kafka, HTTP, etc.), implement `OutboxPublisher` and register
it as a `@Bean` with the same name as the one referenced in
`crablet.outbox.topics.topics.<topic>.publishers`.

## Configuration

### Application Properties

```properties
# Crablet Views
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100

# Crablet Automations
crablet.automations.enabled=true
crablet.automations.polling-interval-ms=1000
crablet.automations.batch-size=100

# Crablet Outbox
crablet.outbox.enabled=true
crablet.outbox.polling-interval-ms=1000
crablet.outbox.topics.topics.wallet-events.publishers=LogPublisher

# Crablet Metrics (optional)
crablet.metrics.enabled=false
```

### View Configuration

View subscriptions are configured in `ViewConfiguration.java`:
- Each view subscribes to specific event types
- Uses tag-based filtering (wallet_id, from_wallet_id, to_wallet_id)
- Views process events asynchronously with leader election

## Database Schema

### Crablet Core Tables
- `events` - Event store (from V1__eventstore_schema.sql)
- `commands` - Command audit trail
- `view_progress` - View processing progress (from V3__view_progress_schema.sql)
- `outbox_topic_progress` - Outbox per-publisher progress tracking (from V13)

### Application View Tables
- `wallet_balance_view` - Wallet balances (V4)
- `wallet_transaction_view` - Transaction history (V5)
- `wallet_summary_view` - Aggregated statistics (V6)
- `wallet_statement_view` - Statement periods with period totals (V8)
- `statement_transactions` - Junction table for idempotent event tracking (V8)

## Leader Election

View projections use PostgreSQL advisory locks for leader election:
- Only one instance processes each view at a time
- Automatic failover if leader crashes (5-30 seconds)
- See [Leader Election Guide](../docs/LEADER_ELECTION.md) for details

**Recommended deployment:**
- Default to **1 application instance per cluster**
- Additional replicas provide standby behavior and failover, not higher throughput for the same processors
- Add replicas only when you explicitly want that operational tradeoff

## Example Usage

### 1. Open a Wallet
```bash
curl -X POST http://localhost:8080/api/wallets \
  -H "Content-Type: application/json" \
  -d '{
    "walletId": "wallet-1",
    "owner": "Alice",
    "initialBalance": 100
  }'
```

### 2. Deposit Money
```bash
curl -X POST http://localhost:8080/api/wallets/wallet-1/deposits \
  -H "Content-Type: application/json" \
  -d '{
    "depositId": "deposit-1",
    "amount": 50,
    "description": "Salary"
  }'
```

### 3. Query Balance
```bash
curl http://localhost:8080/api/wallets/wallet-1
```

### 4. View Transactions
```bash
curl http://localhost:8080/api/wallets/wallet-1/transactions
```

### 5. Manage View Projections
```bash
# Check view status
curl http://localhost:8080/api/views/wallet-balance-view/status

# Get detailed progress
curl http://localhost:8080/api/views/wallet-balance-view/details

# Pause view for maintenance
curl -X POST http://localhost:8080/api/views/wallet-balance-view/pause

# Resume view processing
curl -X POST http://localhost:8080/api/views/wallet-balance-view/resume
```

## Building and Testing

### Building

This is an example application and is not part of the main reactor build. Build it separately:

```bash
# From wallet-example-app directory
cd wallet-example-app

# Ensure library modules are installed first (from project root: make install)
# Then build:
../mvnw install
```

### Testing

Run tests:
```bash
cd wallet-example-app
../mvnw test
```

### Running

Run the application:
```bash
cd wallet-example-app
../mvnw spring-boot:run
```

**Note:** You can also use `make start` or `make wallet-dev` from the project root for convenience.

## See Also

- **[Crablet EventStore](../crablet-eventstore/README.md)** - Core event sourcing library
- **[Crablet Command](../crablet-commands/README.md)** - Command handling with DCB pattern
- **[Crablet Views](../crablet-views/README.md)** - View projections
- **[Crablet Automations](../crablet-automations/README.md)** - Event-driven automations
- **[Crablet Outbox](../crablet-outbox/README.md)** - Transactional outbox pattern
- **[Shared Examples Domain](../shared-examples-domain/README.md)** - Example domains (wallet, course, notification)
- **[Leader Election](../docs/LEADER_ELECTION.md)** - Leader election mechanism
- **[Build Guide](../docs/BUILD.md)** - Build instructions
