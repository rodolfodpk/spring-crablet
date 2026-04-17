# Quickstart

This is the fastest path to seeing Crablet work end to end.

The recommended learning setup is:

- one application instance
- commands, views, automations, and outbox running together
- the wallet example app as the reference application

## Prerequisites

- Java 25
- PostgreSQL 17+
- Maven wrapper is included

## Start The Project

The wallet example app uses the datasource in `wallet-example-app/src/main/resources/application.properties`, which defaults to:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Create that database first, or update the datasource settings before starting the app.

From the repository root:

```bash
make install
make start
```

This installs the library modules, builds the shared example domain, and starts the wallet example application used as the learning/reference app.

## Create A Wallet

```bash
curl -X POST http://localhost:8080/api/wallets \
  -H 'Content-Type: application/json' \
  -d '{
    "walletId": "wallet-123",
    "owner": "Jane Doe",
    "initialBalance": 100
  }'
```

Expected outcome:

- a `WalletOpened` event is appended
- the command returns successfully
- the balance view starts catching up asynchronously

## Deposit Money

```bash
curl -X POST http://localhost:8080/api/wallets/wallet-123/deposits \
  -H 'Content-Type: application/json' \
  -d '{
    "depositId": "deposit-001",
    "amount": 25,
    "description": "Initial top-up"
  }'
```

Expected outcome:

- a `DepositMade` event is appended
- the wallet balance increases
- the transaction view and summary view advance asynchronously

## Query The Read Model

```bash
curl http://localhost:8080/api/wallets/wallet-123
```

Expected response shape:

```json
{
  "walletId": "wallet-123",
  "owner": "Jane Doe",
  "balance": 125,
  "lastUpdatedAt": "2026-04-12T18:00:00Z"
}
```

## Inspect The Example App

Use the reference application as the concrete guide for your first integration:

- App guide: [Wallet Example App](../wallet-example-app/README.md)
- Tutorial path: [Tutorial](TUTORIAL.md)
- Command-side-first adoption: [Commands-First Adoption](COMMANDS_FIRST_ADOPTION.md)

## Next Step

Once the single-instance flow makes sense, decide which adoption path you want:

- learn the full stack locally: [Learning Mode](LEARNING_MODE.md)
- adopt the command side first: [Commands-First Adoption](COMMANDS_FIRST_ADOPTION.md)
- review production topology constraints: [Deployment Topology](DEPLOYMENT_TOPOLOGY.md)
