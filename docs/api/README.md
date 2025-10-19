# API Reference

Base URL: `http://localhost:8080/api`

## Endpoints

| Method | Endpoint                         | Description                     |
|--------|----------------------------------|---------------------------------|
| PUT    | /api/wallets/{walletId}          | Create wallet                   |
| POST   | /api/wallets/{walletId}/deposit  | Deposit money                   |
| POST   | /api/wallets/{walletId}/withdraw | Withdraw money                  |
| POST   | /api/wallets/transfer            | Transfer between wallets        |
| GET    | /api/wallets/{walletId}          | Get wallet state                |
| GET    | /api/wallets/{walletId}/events   | Get event history               |
| GET    | /api/wallets/{walletId}/commands | Get command history with events |

## Example Workflow

### 1. Create Wallet

```bash
curl -X PUT http://localhost:8080/api/wallets/user123 \
  -H "Content-Type: application/json" \
  -d '{"owner": "John Doe", "initialBalance": 1000}'
```

### 2. Deposit Money

```bash
curl -X POST http://localhost:8080/api/wallets/user123/deposit \
  -H "Content-Type: application/json" \
  -d '{"depositId": "dep001", "amount": 500, "description": "Salary"}'
```

### 3. Withdraw Money

```bash
curl -X POST http://localhost:8080/api/wallets/user123/withdraw \
  -H "Content-Type: application/json" \
  -d '{"withdrawalId": "wd001", "amount": 100, "description": "ATM"}'
```

### 4. Transfer Money

```bash
curl -X POST http://localhost:8080/api/wallets/transfer \
  -H "Content-Type: application/json" \
  -d '{"transferId": "tx001", "fromWalletId": "user123", "toWalletId": "user456", "amount": 200, "description": "Payment"}'
```

### 5. Get Wallet State

```bash
curl http://localhost:8080/api/wallets/user123
```

### 6. Get Event History

```bash
curl "http://localhost:8080/api/wallets/user123/events?page=0&size=10"
```

### 7. Get Command History

```bash
curl "http://localhost:8080/api/wallets/user123/commands?page=0&size=10"
```

## Request Bodies

### OpenWalletRequest

```json
{
  "owner": "string",
  "initialBalance": 1000
}
```

### DepositRequest

```json
{
  "depositId": "string",
  "amount": 500,
  "description": "string"
}
```

### WithdrawRequest

```json
{
  "withdrawalId": "string",
  "amount": 100,
  "description": "string"
}
```

### TransferRequest

```json
{
  "transferId": "string",
  "fromWalletId": "string",
  "toWalletId": "string",
  "amount": 200,
  "description": "string"
}
```

## Response Examples

### Command History Response

```json
{
  "commands": [
    {
      "transactionId": "123456",
      "type": "deposit",
      "data": {
        "depositId": "dep001",
        "walletId": "user123",
        "amount": 500,
        "description": "Salary"
      },
      "occurredAt": "2025-10-14T12:00:00Z",
      "events": [
        {
          "type": "DepositMade",
          "occurredAt": "2025-10-14T12:00:00Z",
          "data": {
            "depositId": "dep001",
            "walletId": "user123",
            "amount": 500,
            "newBalance": 1500,
            "description": "Salary"
          }
        }
      ]
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

## Interactive Documentation

For interactive API documentation, use Swagger UI: http://localhost:8080/swagger-ui/index.html