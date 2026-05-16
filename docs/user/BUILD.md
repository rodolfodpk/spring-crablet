# Building Crablet

Tests use Testcontainers (no external dependencies required, Docker must be running).

## Quick Start

For the library build:

```bash
make install   # build everything in the right order
```

For full local validation before a release or large merge:

```bash
make validate-all   # framework, docs, codegen, and standalone examples
```

For the example application:

```bash
createdb wallet_db
make start
```

`make start` only runs `examples/wallet-example-app`; it does not provision PostgreSQL for you. The default datasource is `jdbc:postgresql://localhost:5432/wallet_db`.

## Module Structure

```
── reactor (built together) ──────────────────────────────
  crablet-observability       shared observation names/tags, no runtime deps
  crablet-eventstore           core → observability
  crablet-commands              → eventstore + observability
  crablet-commands-web         → commands + spring-webmvc
  crablet-event-poller      → eventstore + observability
  crablet-outbox               → eventstore + event-processor + observability
  crablet-views                → eventstore + event-processor + observability
  crablet-automations          → eventstore + event-processor + command + observability
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
It carries the framework database migrations so every module gets them automatically
through a single test-scope dependency — no per-module copies needed.

## Build Order

`make install` executes these steps in sequence:

| Step | Target | What it builds |
|------|--------|----------------|
| 1 | `build-core` | `crablet-observability` and `crablet-eventstore` (no tests, installs stub JARs first) |
| 2 | `build-test-support` | `crablet-test-support` (full build) |
| 3 | `build-command` | `crablet-commands` (no tests) |
| 4 | `build-shared` | `shared-examples-domain` (full build with tests) |
| 5 | `build-reactor` | all reactor modules (full build with tests) |

Neither `examples/wallet-example-app` nor `examples/course-example-app` is part of `make install`. Both depend on the reactor being installed first — see [Running the Example Apps](#running-the-example-apps).

## Makefile Commands

```bash
make install            # full build with unit tests (recommended)
make install-all-tests  # full build including integration tests
make validate-all       # full local validation: framework, docs, codegen, examples
make test               # run all tests (requires prior install)
make examples-check     # test standalone wallet and course example apps
make clean              # clean all build artifacts
make start              # run wallet-example-app (port 8080)
make course-start       # run course-example-app (port 8081)
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

## Running the Example Apps

Both example apps are standalone Spring Boot applications excluded from the reactor.
They inherit version management from the parent POM via the local Maven repository,
so `make install` must run first.

### Wallet example (port 8080)

Demonstrates single-aggregate patterns: open wallet, deposit, withdraw, transfer.

```bash
make install
createdb wallet_db
make start          # or: cd examples/wallet-example-app && ../../mvnw spring-boot:run
```

Once running:
- API: http://localhost:8080/api/
- Swagger UI: http://localhost:8080/swagger-ui.html

### Course example (port 8081)

Demonstrates multi-entity DCB: one command enforces both course capacity and student
subscription limit in a single consistency boundary.

```bash
make install
createdb course_db
make course-start   # or: cd examples/course-example-app && ../../mvnw spring-boot:run
```

Once running:
- API: http://localhost:8081/api/courses/{courseId}
- Commands: POST http://localhost:8081/api/commands (define_course, register_student, subscribe_student_to_course, change_course_capacity)
- Swagger UI: http://localhost:8081/swagger-ui.html

## Testing

Unit tests use `InMemoryEventStore` — no Docker needed, complete in under 10ms.
Integration tests start a real PostgreSQL container via Testcontainers (100–500ms, Docker required).

```bash
make install            # runs unit tests only
make install-all-tests  # runs unit + integration tests
make examples-check     # runs standalone example app tests
make validate-all       # runs all local validation targets
```

## Database Migrations

All framework migrations live in a single place:

```
crablet-test-support/src/main/resources/db/migration/
  V1__crablet_eventstore_schema.sql        events, commands, event_tags, indexes, append functions
  V2__crablet_poller_progress_schema.sql   outbox, view, automation, shared-fetch progress tables
```

Flyway picks these up automatically on the test classpath because every module
has `crablet-test-support` as a test-scope dependency. No copies in individual modules.

Both example apps use a two-location Flyway configuration:
- `classpath:db/migration` — framework tables from `crablet-db-migrations`
- `classpath:db/migration/app` — app-specific views (example apps reserve V100+ to avoid framework version collisions)

Wallet app-specific migrations: `wallet_balance_view`, `wallet_transaction_view`, `wallet_summary_view`, `wallet_statement_view`.
Course app-specific migrations: `course_availability` table.
