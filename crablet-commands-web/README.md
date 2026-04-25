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

**Package-based** (recommended for vertical slice layouts — expose an entire slice at once):

```java
@Configuration
public class AppConfig {

    @Bean
    CommandApiExposedCommands commandApiExposedCommands() {
        // All commands whose package starts with this prefix are exposed.
        // New commands added to the package are picked up automatically.
        return CommandApiExposedCommands.fromPackages("com.myapp.wallet");
    }
}
```

Multiple packages are supported:

```java
return CommandApiExposedCommands.fromPackages(
        "com.myapp.wallet",
        "com.myapp.account"
);
```

**Explicit class list** (fine-grained control):

```java
@Bean
CommandApiExposedCommands commandApiExposedCommands() {
    return CommandApiExposedCommands.of(
            OpenWalletCommand.class,
            DepositCommand.class
    );
}
```

That is the only configuration required. Spring Boot auto-configuration wires the endpoint.

**2. Optionally configure the base path** (default: `/api/commands`):

```properties
crablet.commands.api.base-path=/api/commands
```

**3. Optionally enable HTTP correlation headers** (default: disabled):

```properties
crablet.commands.api.correlation-header-enabled=true
# optional; default shown
crablet.commands.api.correlation-header-name=X-Correlation-Id
```

When enabled, the generic command API accepts a UUID correlation header, echoes the effective
correlation ID on responses, and stores it on appended events. If the header is missing, the API
generates one. A malformed correlation header returns `400 Bad Request`.

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

## Management endpoint

`GET /api/commands` returns the list of currently exposed commands sorted by type name:

```json
{
  "exposedCommands": [
    { "commandType": "deposit",     "className": "com.myapp.wallet.DepositCommand" },
    { "commandType": "open_wallet", "className": "com.myapp.wallet.OpenWalletCommand" }
  ]
}
```

Useful for debugging `fromPackages` resolution — quickly confirms which commands are actually reachable.

## Swagger / OpenAPI

Add the optional springdoc dependency to activate the integration:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

When present, the module automatically enriches the `POST /api/commands` OpenAPI operation
with a `oneOf` schema built from the exposed command classes:

```yaml
POST /api/commands
  requestBody:
    oneOf:
      - $ref: '#/components/schemas/DepositCommand'
      - $ref: '#/components/schemas/OpenWalletCommand'
    discriminator:
      propertyName: commandType
```

Swagger UI renders this as a dropdown — select a command type and the matching fields appear.
No extra configuration is needed; the integration activates automatically.

## Access control

Only commands matching the `CommandApiExposedCommands` filter are reachable.
All other known command types return `404`. There is no generic write endpoint for events.

## Dependencies

- `crablet-commands` (required)
- `spring-webmvc` (required — this is an HTTP adapter)
- `jackson-databind` (required)
- `springdoc-openapi-starter-webmvc-ui` (optional — enables Swagger UI integration)
