# Create A New Crablet App Manually

This guide shows how to create a Crablet application directly against the runtime
APIs. Use it for brownfield additions, explicit module layout control, or learning
the APIs that generated applications target.

For the recommended event-model-to-app path, start with
[AI-First Workflow](ai-tooling/AI_FIRST_WORKFLOW.md) and [Event Model Format](ai-tooling/EVENT_MODEL_FORMAT.md).

## Prerequisites

- Java 25
- Maven
- PostgreSQL
- Either the Spring Boot CLI or `curl`

Crablet currently uses `1.0.0-SNAPSHOT`. Until Crablet artifacts are published,
install them into your local Maven repository before using them from a separate
sample app:

```bash
./mvnw install -DskipTests
```

Run that command from the Crablet repository.

## Generate The Spring Boot App Manually

With the Spring Boot CLI:

```bash
spring init \
  --build=maven \
  --java-version=25 \
  --dependencies=web,jdbc,flyway,postgresql,validation \
  my-crablet-app
```

If you do not have the Spring Boot CLI installed, use Spring Initializr directly:

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=25 \
  -d dependencies=web,jdbc,flyway,postgresql,validation \
  -d groupId=com.example \
  -d artifactId=my-crablet-app \
  -d name=my-crablet-app \
  -o my-crablet-app.zip
```

Unzip the archive and open the generated project:

```bash
unzip my-crablet-app.zip -d my-crablet-app
cd my-crablet-app
```

Spring dependency identifiers can change across Spring Boot versions. If a
command fails, run `spring init --list` or inspect [Spring Initializr](https://start.spring.io) for
the current identifiers.

## Add Crablet Dependencies

Start with the command side:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-eventstore</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-commands</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

If you want a generic HTTP endpoint for commands, also add:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-commands-web</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Do not add views, outbox, automations, or metrics until the write path is
working. Those modules add poller-backed runtime behavior and should be adopted
intentionally.

## Configure The Database

Point Spring Boot at PostgreSQL:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/my_crablet_app
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.flyway.enabled=true
```

Create the database before starting the app:

```bash
createdb my_crablet_app
```

Keep application-specific Flyway migrations in the generated app. Crablet modules
provide runtime wiring through Spring Boot auto-configuration when the
dependencies are on the classpath, but the PostgreSQL schema is still owned by
your application migrations.

For the event store schema, use [Database Schema](../crablet-eventstore/SCHEMA.md)
as the reference and copy the current SQL shape into your app's first migration.
The wallet example's
[`V1__eventstore_schema.sql`](../wallet-example-app/src/main/resources/db/migration/V1__eventstore_schema.sql)
is the runnable reference in this repository.

## Build The First Manual Vertical Slice

For the first feature, keep the surface small:

1. Define one command type.
2. Define one event record.
3. Implement one `CommandHandler`.
4. Expose the command through either a small controller or `crablet-commands-web`.
5. Submit the command and verify that an event was written.

With `crablet-commands-web`, expose only the commands you want reachable over
HTTP:

```java
@Configuration
class CommandApiConfig {

    @Bean
    CommandApiExposedCommands commandApiExposedCommands() {
        return CommandApiExposedCommands.of(OpenAccountCommand.class);
    }
}
```

Requests go to `POST /api/commands` by default and include a `commandType` field
plus the command-specific payload. See
[crablet-commands-web](../crablet-commands-web/README.md) for the full request
contract.

## Generator Reference

`embabel-codegen` is a fat JAR CLI that generates all structural Crablet artifacts
from `event-model.yaml`. It is built as part of this repository and exposed as an
MCP tool via `.claude/settings.json`.

Supported artifacts:

- sealed event interface + records
- command records with YAVI validation
- state projectors and command handlers (DCB pattern)
- `AbstractTypedViewProjector` + Flyway SQL migration per view
- `AutomationHandler` per automation
- `OutboxPublisher` per outbox spec

After generation, only business rules not captured in the model require manual editing.
Prefer improving `event-model.yaml` over editing generated structural code by hand.

## Next Reading

- [AI-First Workflow](ai-tooling/AI_FIRST_WORKFLOW.md)
- [Event Model Format](ai-tooling/EVENT_MODEL_FORMAT.md)
- [Commands-First Adoption](COMMANDS_FIRST_ADOPTION.md)
- [Tutorial](TUTORIAL.md)
- [Quickstart](QUICKSTART.md)
- [Wallet Example App](../wallet-example-app/README.md)
