# Test Organization Strategy

## Overview

This document describes the test organization strategy for the wallet system, which follows a **scope-based approach** rather than mirroring the production package structure.

## Package Structure

```
src/test/java/
├── unit/                                    # Fast, isolated tests
│   ├── domain/                              # Domain logic tests
│   ├── features/                            # Feature handler unit tests
│   │   ├── deposit/
│   │   ├── withdraw/
│   │   ├── transfer/
│   │   ├── openwallet/
│   │   └── query/
│   ├── infrastructure/                      # Infrastructure unit tests
│   │   ├── crablet/
│   │   ├── web/
│   │   ├── database/
│   │   ├── jackson/
│   │   └── resilience/
│   └── validation/                          # Validation logic tests
├── integration/                             # Full-stack tests
│   ├── api/                                 # REST API tests
│   │   ├── controller/                      # Controller integration tests
│   │   └── query/                           # Query endpoint tests
│   ├── database/                            # Database integration tests
│   ├── features/                            # Feature integration tests
│   │   └── transfer/
│   └── crosscutting/                        # Cross-cutting concerns
│       ├── concurrency/
│       ├── errorhandling/
│       ├── idempotency/
│       └── performance/
├── architecture/                            # Architecture validation tests
└── testutils/                               # Shared test utilities
```

## Test Types

### Unit Tests (`unit.*`)

**Purpose**: Test individual components in isolation with fast execution.

**Characteristics**:
- Fast execution (< 100ms per test)
- No external dependencies (database, network)
- Use mocks/stubs for dependencies
- Test business logic, validation, and algorithms

**Examples**:
- `unit.features.deposit.DepositCommandHandlerTest` - Tests deposit logic
- `unit.infrastructure.jackson.JacksonIT` - Tests JSON serialization
- `unit.infrastructure.resilience.Resilience4jTest` - Tests resilience configuration

### Integration Tests (`integration.*`)

**Purpose**: Test component interactions and full-stack scenarios.

**Characteristics**:
- Slower execution (100ms - 5s per test)
- Use real dependencies (database, Spring context)
- Test API endpoints, database operations, cross-cutting concerns
- Verify system behavior under realistic conditions

**Examples**:
- `integration.api.controller.DepositControllerIT` - Tests REST API
- `integration.database.DatabaseErrorHandlingIT` - Tests database error scenarios
- `integration.features.transfer.TransferIT` - Tests complete transfer workflow
- `integration.crosscutting.concurrency.WalletConcurrencyIT` - Tests concurrent operations

### Architecture Tests (`architecture.*`)

**Purpose**: Validate architectural constraints and design principles.

**Characteristics**:
- Use ArchUnit for structural validation
- Test package dependencies, naming conventions, annotations
- Ensure architectural rules are followed

**Examples**:
- `architecture.DomainArchitectureTest` - Validates domain layer rules
- `architecture.FeatureSliceArchitectureTest` - Validates feature slice boundaries

## Naming Conventions

### File Naming
- **Unit tests**: `*Test.java` (e.g., `DepositCommandHandlerTest.java`)
- **Integration tests**: `*IT.java` (e.g., `DepositControllerIT.java`)
- **Architecture tests**: `*Test.java` (e.g., `DomainArchitectureTest.java`)

### Class Naming
- **Unit tests**: `*Test` (e.g., `DepositCommandHandlerTest`)
- **Integration tests**: `*IT` (e.g., `DepositControllerIT`)
- **Architecture tests**: `*Test` (e.g., `DomainArchitectureTest`)

## Visibility Strategy

### Production Code Visibility

**Public Classes** (accessible from tests in any package):
- Controllers (`*Controller`) - REST API boundaries
- Command Handlers (`*CommandHandler`) - Spring components
- Commands (`*Command`) - Used by framework and tests
- DTOs (`*Request`, `*Response`) - API contracts

**Package-Private Classes** (future internal helpers):
- Validators, calculators, mappers
- Internal implementation details
- Not directly tested (tested via public consumers)

### Test Access Strategy

- **Unit tests**: Access public domain classes directly
- **Integration tests**: Access public APIs (REST endpoints, Spring beans)
- **No package-private access needed**: All testable functionality is public

## Benefits

### Clear Boundaries
- **Unit tests**: Fast, isolated, focused on logic
- **Integration tests**: Realistic, full-stack, focused on behavior
- **Architecture tests**: Structural validation, focused on design

### Easy Navigation
- Find tests by **scope** (unit vs integration) rather than production package
- Clear separation of concerns
- Consistent naming conventions

### Maintainability
- Tests organized by **what they test** (scope) not **where they test** (package)
- Easy to add new tests in appropriate categories
- Clear guidelines for test placement

## Migration History

The test organization was migrated from a confusing structure with three parallel hierarchies:

**Before** (confusing):
```
src/test/java/
├── com/wallets/domain/          # Mirror package structure
├── unit/                        # Scope-based structure  
├── integration/                 # Scope-based structure
└── architecture/                # Architecture tests
```

**After** (clear):
```
src/test/java/
├── unit/                        # All unit tests
├── integration/                 # All integration tests
└── architecture/                # All architecture tests
```

This migration eliminated redundancy, improved clarity, and established consistent patterns for future test development.

## Guidelines for New Tests

### When to Create Unit Tests
- Testing business logic in command handlers
- Testing validation rules
- Testing utility functions
- Testing infrastructure components in isolation

### When to Create Integration Tests
- Testing REST API endpoints
- Testing database operations
- Testing cross-cutting concerns (concurrency, error handling)
- Testing complete workflows

### When to Create Architecture Tests
- Validating package dependencies
- Ensuring naming conventions
- Checking annotation usage
- Validating architectural constraints

## Running Tests

```bash
# Run all tests
mvn test

# Run only unit tests
mvn test -Dtest="unit.**"

# Run only integration tests  
mvn test -Dtest="integration.**"

# Run only architecture tests
mvn test -Dtest="architecture.**"

# Run full build with integration tests
mvn clean install
```
