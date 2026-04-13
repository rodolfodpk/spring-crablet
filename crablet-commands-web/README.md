# Crablet Commands Web

Generic HTTP command API adapter for the Crablet command framework.
Exposes selected commands as a `POST /api/commands` endpoint backed by the existing `CommandExecutor`.

## When to use

Add this module when your application wants to accept commands over HTTP without writing a
custom controller per command type. It is a pure delivery adapter — all consistency and
business logic remain in the command handlers.

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-commands-web</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Setup

**1. Declare which commands are reachable over HTTP** (required):

```java
@Configuration
public class AppConfig {

    @Bean
    CommandApiExposedCommands commandApiExposedCommands() {
        return CommandApiExposedCommands.of(
                OpenWalletCommand.class,
                DepositCommand.class
        );
    }
}
```

That is the only configuration required. Spring Boot auto-configuration wires the endpoint.

**2. Optionally configure the base path** (default: `/api/commands`):

```properties
crablet.commands.api.base-path=/api/commands
```

## Request format

Every request is a `POST` with a JSON body containing a `commandType` field plus the
command-specific fields:

```http
POST /api/commands
Content-Type: application/json

{
  "commandType": "open_wallet",
  "walletId": "wallet-123",
  "owner": "Alice",
  "initialBalance": 100
}
```

## Response codes

| Status | Meaning |
|--------|---------|
| `201 Created` | Command executed, new event stored |
| `200 OK` | Idempotent duplicate detected — `{"status":"IDEMPOTENT","reason":"..."}` |
| `400 Bad Request` | Malformed JSON, missing `commandType`, unknown type, or invalid payload |
| `404 Not Found` | Command type is known but not in the exposed allowlist |
| `409 Conflict` | DCB concurrency conflict |

## Access control

Only command types explicitly listed in the `CommandApiExposedCommands` bean are reachable.
All other known command types return `404`. There is no generic write endpoint for events.

## Dependencies

- `crablet-commands` (required)
- `spring-webmvc` (required — this is an HTTP adapter)
- `jackson-databind` (required)
