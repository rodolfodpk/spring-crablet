# Crablet Commands Web

Generic HTTP command API adapter for the Crablet command framework.
Exposes selected commands as a `POST /api/commands` endpoint backed by the existing `CommandExecutor`.

## When to use

Add this module when your application wants to accept commands over HTTP without writing a
custom controller per command type. It is a pure delivery adapter — all consistency and
business logic remain in the command handlers.

## vs. custom REST controllers

Both approaches are valid — choose based on what your HTTP API needs to express.

**Use `crablet-commands-web`** when the generic `commandType`-dispatch pattern is enough:
- Internal tooling, admin panels, or service-to-service calls
- Rapid prototyping where endpoint shape doesn't matter yet
- Callers that already understand the `commandType` convention

**Write a custom `@RestController`** with request/response DTOs when you need:
- Domain-specific URLs (`POST /api/wallets/{id}/deposits` vs `POST /api/commands`)
- Custom response bodies beyond `{"status":"CREATED"}`
- Per-field validation with `@Valid` / `@NotBlank` on DTO fields
- Fine-grained Swagger/OpenAPI annotations per endpoint
- Public-facing APIs consumed by clients not aware of the `commandType` convention

Custom controllers inject `CommandExecutor` directly — no extra framework plumbing:

```java
@RestController
@RequestMapping("/api/wallets")
class WalletController {

    private final CommandExecutor commandExecutor;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WalletResponse openWallet(@RequestBody @Valid OpenWalletRequest req) {
        commandExecutor.execute(
            new OpenWalletCommand(req.walletId(), req.owner(), req.initialBalance()));
        return WalletResponse.of(req.walletId(), req.owner(), req.initialBalance());
    }
}
```

**Both can coexist.** A common pattern: use `crablet-commands-web` for writes and add a
custom `@RestController` for reads — query results need domain-shaped response DTOs anyway.
The wallet-example-app follows this pattern: writes go through `POST /api/commands`, reads
go through `WalletQueryController` with its `WalletResponse` DTO.

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
