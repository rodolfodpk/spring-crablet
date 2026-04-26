# Building Crablet

Tests use Testcontainers (no external dependencies required, Docker must be running).

## Quick Start

For the library build:

```bash
make install   # build everything in the right order
```

For the example application:

```bash
createdb wallet_db
make start
```

`make start` only runs `wallet-example-app`; it does not provision PostgreSQL for you. The default datasource is `jdbc:postgresql://localhost:5432/wallet_db`.

## Module Structure

```
── reactor (built together) ──────────────────────────────
  crablet-eventstore           core, no framework deps
  crablet-commands              → eventstore
  crablet-commands-web         → commands + spring-webmvc
  crablet-event-poller      → eventstore
  crablet-outbox               → eventstore + event-processor
  crablet-views                → eventstore + event-processor
  crablet-automations          → eventstore + event-processor + command
  crablet-metrics-micrometer   → eventstore

── outside reactor (built separately) ───────────────────
  crablet-test-support         → eventstore
  shared-examples-domain       → eventstore + command
  wallet-example-app           → everything
```

Modules outside the reactor are excluded because they would create cyclic
dependencies if included. The Makefile resolves this with a specific build order.

## Why the Build Order Matters

`shared-examples-domain` depends on `crablet-eventstore` and `crablet-commands`
to define the wallet and course example domains. At the same time, framework
modules depend on `shared-examples-domain` in test scope to run realistic domain
tests. This creates a build-time cycle that Maven cannot resolve on its own.

**Resolution:** the Makefile builds framework modules first (skipping tests),
then `shared-examples-domain`, then runs the full reactor with all tests.
Stub JARs are used temporarily so Maven's dependency resolution doesn't fail
before the real JARs are built.

`crablet-test-support` sits outside the reactor because it depends on
`crablet-eventstore` (main scope) and provides test utilities to all other modules.
It carries all database migrations (V1–V6) so every module gets them automatically
through a single test-scope dependency — no per-module copies needed.

## Build Order

`make install` executes these steps in sequence:

| Step | Target | What it builds |
|------|--------|----------------|
| 1 | `build-core` | `crablet-eventstore` (no tests, installs stub JARs first) |
| 2 | `build-test-support` | `crablet-test-support` (full build) |
| 3 | `build-command` | `crablet-commands` (no tests) |
| 4 | `build-shared` | `shared-examples-domain` (full build with tests) |
| 5 | `build-reactor` | all reactor modules (full build with tests) |

`wallet-example-app` is not part of `make install`. It depends on the reactor
being installed first — see [Running the Example App](#running-the-example-app).

## Makefile Commands

```bash
make install            # full build with unit tests (recommended)
make install-all-tests  # full build including integration tests
make test               # run all tests (requires prior install)
make clean              # clean all build artifacts
make start              # run wallet-example-app
```

Advanced targets (for troubleshooting or incremental builds):

```bash
make build-core          # step 1: install crablet-eventstore without tests
make build-test-support  # step 2: build crablet-test-support
make build-command       # step 3: install crablet-commands without tests
make build-shared        # step 4: build shared-examples-domain
make build-reactor       # step 5: build all reactor modules
```

## Maven Commands

If you prefer Maven directly, follow the same order as the Makefile:

```bash
# Step 1: install stubs and build eventstore
./mvnw install -N -q
./mvnw clean install -pl crablet-eventstore -DskipTests

# Step 2: build test support
cd crablet-test-support && ../mvnw install && cd ..

# Step 3: build command
./mvnw install -pl crablet-commands -DskipTests

# Step 4: build shared examples
cd shared-examples-domain && ../mvnw install && cd ..

# Step 5: build reactor
./mvnw install
```

Run a single module after `make install`:

```bash
./mvnw test -pl crablet-views
./mvnw test -pl crablet-commands -Dtest=DepositCommandHandlerTest
```

## Running the Example App

```bash
# Option 1 — easiest
make install
createdb wallet_db
make start

# Option 2 — manual
make install
createdb wallet_db
cd wallet-example-app && ../mvnw spring-boot:run
```

`wallet-example-app` is a standalone Spring Boot application excluded from the
reactor. It inherits version management from the parent POM via the local Maven
repository, so `make install` must run first.

Once running:
- API: http://localhost:8080/api/
- Swagger UI: http://localhost:8080/swagger-ui.html

## Testing

Unit tests use `InMemoryEventStore` — no Docker needed, complete in under 10ms.
Integration tests start a real PostgreSQL container via Testcontainers (100–500ms, Docker required).

```bash
make install            # runs unit tests only
make install-all-tests  # runs unit + integration tests
```

## Database Migrations

All framework migrations live in a single place:

```
crablet-test-support/src/main/resources/db/migration/
  V1__eventstore_schema.sql        events + commands tables
  V2__outbox_schema.sql            outbox_topic_progress table
  V3__view_progress_schema.sql     view_progress table
  V4__automation_progress_schema.sql automation_progress table
  V5__correlation_causation.sql    correlation_id / causation_id on events
  V6__shared_fetch_scan_progress.sql  module + processor scan progress (shared-fetch)
```

Flyway picks these up automatically on the test classpath because every module
has `crablet-test-support` as a test-scope dependency. No copies in individual modules.

`wallet-example-app` manages its own migrations in `src/main/resources/db/migration/`
(V1 eventstore, V3+ view progress and app views, outbox, automations, correlation, shared-fetch, etc.).
