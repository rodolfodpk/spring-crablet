# Crablet App Template

Starter Spring Boot project for building a Crablet application. Commands, events, views,
automations, and outbox publishers are written directly in Java against the framework's public
API — see the `/crablet-app-dev` Claude Code skill for the feature-slice workflow.

## Prerequisites

- Java 25
- PostgreSQL

## Runtime Setup

The default datasource is:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/crablet_app
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Create the database before running the app:

```bash
createdb crablet_app
```

Then:

```bash
./mvnw spring-boot:run
```

The starter POM includes the command, command-web, event store, and views modules. Add
`crablet-automations` or `crablet-outbox` when your app needs event-driven reactions or external
publication. If automations use `ViewBackedAutomationHandler`, keep `crablet-views` on the
classpath — the views processor does not need to be enabled in the same process.

## First Slice

Work one vertical slice at a time:

1. Write the command record, its validation, and the command handler.
2. Write the event record(s) the handler appends, with tags matching the consistency boundary.
3. Write the state projector / view needed to observe the outcome, if any.
4. Write the automation or outbox publisher, if the slice needs a reaction or external effect.
5. Write handler/view/automation tests.
6. Run `./mvnw verify`.

See the `/crablet-app-dev` and `/crablet-dcb` Claude Code skills for the choice guide between
`idempotent`, `commutative`, and `non-commutative` command patterns.

## Local Commands

```bash
./mvnw verify   # full Maven test run
```

## Rules Of Thumb

- Model one vertical slice at a time.
- Keep external clients, credentials, and retry policy in application-owned adapters, not in
  handlers.
- Automation handlers and outbox publishers should stay focused on their single reaction/publish
  responsibility.

## Optional: AI-Assisted Codegen (pré-1.0/experimental)

A separate, optional pré-1.0/experimental track generates structural code from an
`event-model.yaml` description instead of writing it by hand — see
[`docs/dev/PRODUCT_ROADMAP.md`](../../docs/dev/PRODUCT_ROADMAP.md) for current status and the
`/crablet-event-modeling`, `/crablet-codegen`, and `/crablet-diagram-advisor`
(all pré-1.0/experimental) Claude Code skills for the workflow. Build the generator with
`make codegen-build` from the `spring-crablet` root and use the `plan`/`generate`/`sync-scenarios`
Makefile targets. `make diagram-preview` writes a static board preview from `event-model.yaml`
(pré-1.0/experimental, no automated tests — see the roadmap).
