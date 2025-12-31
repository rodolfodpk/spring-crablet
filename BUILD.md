# Building Crablet

Tests use Testcontainers (no external dependencies required).

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
2. Build `shared-examples-domain` (which depends on `crablet-eventstore`)
3. Build all reactor modules with tests (now that `shared-examples-domain` is available)

## Using Maven Directly

**Note:** This project has a cyclic dependency between `crablet-eventstore` and `shared-examples-domain`:
- `shared-examples-domain` depends on `crablet-eventstore` (main scope)
- `crablet-eventstore` depends on `shared-examples-domain` (test scope)

### Why We Have Cyclic Dependencies

The cyclic dependency exists because:

1. **`shared-examples-domain` → `crablet-eventstore` (main scope)**: The example domains (wallet, course) use framework interfaces and annotations from `crablet-eventstore`:
   - Framework interfaces: `StateProjector`, `EventStore`, `Query`
   - Framework annotations: `@PeriodConfig`, `@PeriodType`
   - These are needed for the example implementations to demonstrate framework usage

2. **`crablet-eventstore` → `shared-examples-domain` (test scope)**: Framework modules use the shared examples in their tests:
   - `crablet-eventstore` tests use wallet/course examples
   - `crablet-command` tests use wallet/course examples
   - `crablet-metrics-micrometer` tests use wallet/course examples

### The Trade-off

**Why we chose this approach:**
- ✅ **Consistency**: All framework modules test against the same realistic examples (wallet, course domains)
- ✅ **Maintainability**: Example domains are defined once and reused across all modules
- ✅ **Realistic Testing**: Tests use complete, realistic domain models rather than minimal test fixtures
- ✅ **Documentation**: Examples serve as living documentation of framework usage patterns

**The cost:**
- ⚠️ **Build Complexity**: Requires a specific build order (handled automatically by Makefile)
- ⚠️ **Reactor Exclusion**: `shared-examples-domain` must be excluded from the Maven reactor build
- ⚠️ **Manual Build Steps**: Direct Maven usage requires following the correct build sequence

**Alternative approaches we considered:**
- ❌ **Duplicate examples per module**: Would eliminate the cycle but create maintenance burden and inconsistency
- ❌ **No shared examples**: Would simplify builds but reduce test quality and documentation value
- ❌ **Move examples to separate repo**: Would break the cycle but add complexity for contributors

We chose to accept the build complexity in favor of better maintainability and test quality.

### Manual Build Steps

To build manually with Maven, follow this order:

```bash
# Step 1: Build crablet-eventstore without tests
./mvnw clean install -pl crablet-eventstore -am -DskipTests

# Step 2: Build shared-examples-domain
cd shared-examples-domain && ../mvnw install && cd ..

# Step 3: Build all reactor modules (with tests)
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
make build-core   # Build crablet-eventstore only (skip tests)
make build-shared # Build shared-examples-domain only
make build-reactor # Build all reactor modules
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

