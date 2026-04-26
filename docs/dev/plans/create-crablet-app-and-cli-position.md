# Document New-App Creation And Defer Crablet CLI

## Summary

Add a "Create A New Crablet App" guide that uses official Spring
project-generation tooling first, then layers Crablet setup on top. Do not build
a Crablet CLI yet.

The CLI idea should be documented as a future option: useful only when it adds
Crablet-specific scaffolding beyond what Spring Initializr already provides.

## Key Changes

- Add `docs/user/CREATE_A_CRABLET_APP.md` as the canonical guide for creating a new
  Crablet app in a fresh repository.
- Add one short root `README.md` link to the new guide, keeping the README as a
  compact project front door.
- Keep `docs/user/QUICKSTART.md` focused on running this repository's wallet example.
- Keep `docs/user/COMMANDS_FIRST_ADOPTION.md` focused on adoption strategy and
  command-side-first production guidance.

## Guide Content

- Show two official Spring project-generation paths:
  - Spring Boot CLI: `spring init`
  - Spring Initializr HTTP API: `curl https://start.spring.io/starter.zip`
- Generate a Java 25 Maven Spring Boot app with:
  - Spring Web
  - JDBC
  - Flyway
  - PostgreSQL
  - Validation
- Explain that Crablet currently uses `1.0.0-SNAPSHOT`, so external sample apps
  need Crablet installed locally with `./mvnw install -DskipTests` unless
  artifacts are published.
- Add minimum Crablet dependencies:
  - `crablet-eventstore`
  - `crablet-commands`
  - optional `crablet-commands-web`
- Explain that Crablet auto-configuration wires runtime beans, but the
  application still owns its PostgreSQL schema migrations.
- Build one command-side vertical slice:
  - one command type
  - one event record
  - one `CommandHandler`
  - one HTTP entry point, either custom controller or `crablet-commands-web`
  - one verification step showing events were persisted
- Link to:
  - `docs/user/COMMANDS_FIRST_ADOPTION.md`
  - `docs/user/TUTORIAL.md`
  - `wallet-example-app/README.md`

## CLI Position

Do not implement a `crablet` CLI in this change.

Document this future direction:

- A Crablet CLI may eventually expose `crablet new`.
- It could use local `spring init` when installed.
- It should fall back to the Spring Initializr HTTP API when the Spring CLI is
  unavailable.
- It should only exist if it adds Crablet-specific scaffolding, such as:
  - Crablet dependencies
  - starter configuration
  - example command/event/handler files
  - Flyway setup
  - command-web exposure
  - smoke tests

The CLI should not merely wrap `spring init`.

## Test Plan

- Run `make docs-check`.
- Run `make docs-generate-check`.
- Run `make docs-compile-check`.
- Run a local markdown link sanity check for `README.md` and the new guide.

## Assumptions

- The recommended first new-app path is command-side-first.
- Views, outbox, automations, and metrics are later add-ons.
- The root README should stay compact.
- No public Java API, generated sample repo, or Crablet CLI is added in this
  step.
