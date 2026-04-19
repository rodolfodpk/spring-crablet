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
