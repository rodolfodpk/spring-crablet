# Building Crablet

Tests use Testcontainers (no external dependencies required).

## Quick Reference

**TL;DR:** Run `make install` - handles cyclic dependencies automatically.

**Why cyclic dependencies?** `shared-examples-domain` uses framework interfaces (main scope), while framework modules use examples in tests (test scope). This provides consistency and maintainability at the cost of build complexity (handled by Makefile).

📖 **Details:** See [Cyclic Dependencies](#cyclic-dependencies) section below.

## Quick Start (Recommended)

The easiest way to build the project is using the Makefile, which handles cyclic dependencies automatically:

```bash
make install
```

Or simply:
```bash
make
```

This single command will:
1. Build `crablet-eventstore` (main code only, skipping tests)
2. Build `crablet-test-support` (test utilities module, depends on `crablet-eventstore`)
3. Build `crablet-command` (main code only, skipping tests)
4. Build `shared-examples-domain` (which depends on `crablet-eventstore` and `crablet-test-support`)
5. Build all reactor modules with tests (now that all dependencies are available)

## Using Maven Directly

**Note:** This project has a cyclic dependency between `crablet-eventstore` and `shared-examples-domain`:
- `shared-examples-domain` depends on `crablet-eventstore` (main scope)
- `crablet-eventstore` depends on `shared-examples-domain` (test scope)

### Cyclic Dependencies

The cyclic dependency exists because:
- **`shared-examples-domain` → `crablet-eventstore`**: Example domains use framework interfaces (`StateProjector`, `EventStore`, `Query`) and annotations (`@PeriodConfig`, `@PeriodType`)
- **`crablet-eventstore` → `shared-examples-domain`**: Framework modules use shared examples in tests for consistency and realistic testing

**Trade-off:** Build complexity (requires specific order) vs. maintainability and test quality. The Makefile handles this automatically.

**Note:** The `crablet-test-support` module is excluded from the reactor and built separately because it's part of the dependency chain that breaks the cycle. It contains test utilities (`InMemoryEventStore`, `AbstractCrabletTest`, `DCBTestHelpers`) that are used by all modules in test scope.

### Manual Build Steps

To build manually with Maven, follow this order:

```bash
# Step 1: Build crablet-eventstore without tests
./mvnw clean install -pl crablet-eventstore -am -DskipTests

# Step 2: Build crablet-test-support (test utilities module)
cd crablet-test-support && ../mvnw install && cd ..

# Step 3: Build crablet-command without tests
./mvnw clean install -pl crablet-command -am -DskipTests

# Step 4: Build shared-examples-domain
cd shared-examples-domain && ../mvnw install && cd ..

# Step 5: Build all reactor modules (with tests)
./mvnw install
```

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make install` or `make` | Full build with all tests (handles cyclic dependencies) |
| `make compile` | Compile all modules without packaging |
| `make package` | Build JARs for all modules |
| `make test` | Run all tests |
| `make test-skip` | Build without running tests (faster) |
| `make verify` | Full build with tests and verification |
| `make clean` | Clean all build artifacts |

## Advanced Build Commands

For troubleshooting or incremental builds:

```bash
make build-core        # Build crablet-eventstore only (skip tests)
make build-test-support # Build crablet-test-support only
make build-command     # Build crablet-command only (skip tests)
make build-shared      # Build shared-examples-domain only
make build-reactor     # Build all reactor modules
```

## Building Individual Modules

To build a specific module:

```bash
# Build a single module (with dependencies)
./mvnw install -pl <module-name> -am

# Examples:
./mvnw install -pl crablet-eventstore -am
./mvnw install -pl crablet-command -am
```

## Maven Wrapper

This project uses the Maven wrapper (`mvnw`) to ensure consistent builds across different environments. All Maven commands should use `./mvnw` instead of `mvn` to use the project's specified Maven version.
