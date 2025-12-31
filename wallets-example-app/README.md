# Wallets Example App

A complete example application demonstrating the full Crablet event sourcing stack:
- REST API with OpenAPI documentation
- Command handlers using DCB pattern
- View projections using crablet-views
- Full integration of all Crablet modules

## Overview

This application demonstrates a wallet management system with:
- **Commands**: Open wallet, deposit, withdraw, transfer money
- **Events**: WalletOpened, DepositMade, WithdrawalMade, MoneyTransferred
- **Views**: Three materialized views for different use cases:
  - `wallet_balance_view` - Fast balance lookups
  - `wallet_transaction_view` - Transaction history
  - `wallet_summary_view` - Aggregated statistics

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
```bash
# Build the project (from root)
make install

# Run the application
cd wallets-example-app
../mvnw spring-boot:run
```

The application will:
- Run Flyway migrations (creates Crablet tables + view tables)
- Start the REST API on port 8080
- Start view processors (asynchronous projections)

### Access API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## API Endpoints

### Commands (Write Operations)

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

## View Projections

The application includes three view projections:

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

## Configuration

### Application Properties

```properties
# Crablet EventStore
crablet.eventstore.enabled=true

# Crablet Command
crablet.command.enabled=true

# Crablet Views
crablet.views.enabled=true
crablet.views.polling-interval-ms=1000
crablet.views.batch-size=100

# Crablet Metrics (optional)
crablet.metrics.enabled=true
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

### Application View Tables
- `wallet_balance_view` - Wallet balances (V4)
- `wallet_transaction_view` - Transaction history (V5)
- `wallet_summary_view` - Aggregated statistics (V6)

## Leader Election

View projections use PostgreSQL advisory locks for leader election:
- Only one instance processes each view at a time
- Automatic failover if leader crashes (5-30 seconds)
- See [Leader Election Guide](../LEADER_ELECTION.md) for details

**Recommended deployment:**
- **1 instance**: Works fine in Kubernetes (auto-restart on crash, brief downtime)
- **2+ instances**: Recommended for zero-downtime failover (follower takes over within 5-30 seconds)

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

## Testing

Run tests:
```bash
cd wallets-example-app
../mvnw test
```

## See Also

- **[Crablet EventStore](../crablet-eventstore/README.md)** - Core event sourcing library
- **[Crablet Command](../crablet-command/README.md)** - Command handling with DCB pattern
- **[Crablet Views](../crablet-views/README.md)** - View projections
- **[Leader Election](../LEADER_ELECTION.md)** - Leader election mechanism
- **[Build Guide](../BUILD.md)** - Build instructions

