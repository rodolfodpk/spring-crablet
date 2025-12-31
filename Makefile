# Crablet Multi-Module Project Makefile
# Provides convenient commands for working with the multi-module structure
#
# Note: This project has a cyclic dependency between crablet-eventstore and shared-examples-domain.
# The build process handles this automatically by:
# 1. Building crablet-eventstore without tests first
# 2. Building shared-examples-domain (which depends on crablet-eventstore)
# 3. Building all reactor modules with tests (shared-examples-domain is now available)

.PHONY: help install install-all-tests ci-verify build-all compile package test test-skip clean verify build-core build-shared build-reactor build-reactor-verify start wallet-dev

# Default target
help:
	@echo "Crablet Multi-Module Project"
	@echo ""
	@echo "Build Commands:"
	@echo "  install          - Build and install all modules (handles cyclic dependencies automatically)"
	@echo "  install-all-tests - Build with all tests including integration tests (installs to local repo)"
	@echo "  ci-verify        - Build with all tests for CI (no local repo install, faster)"
	@echo "  build-all        - Alias for install"
	@echo "  compile          - Compile all modules without packaging"
	@echo "  package          - Build JARs for all modules"
	@echo "  verify           - Full build with tests and verification"
	@echo ""
	@echo "Test Commands:"
	@echo "  test        - Run all tests across all modules"
	@echo "  test-skip   - Build without running tests (faster)"
	@echo ""
	@echo "Clean Commands:"
	@echo "  clean       - Clean all build artifacts"
	@echo ""
	@echo "Advanced Build Commands (for troubleshooting):"
	@echo "  build-core  - Build crablet-eventstore without tests and install"
	@echo "  build-shared - Build shared-examples-domain and install"
	@echo "  build-reactor - Build all reactor modules (after core and shared are installed)"
	@echo ""
	@echo "Application Commands:"
	@echo "  start       - Start wallet-example application"
	@echo "  wallet-dev  - Start wallet development environment (alias for start)"
	@echo ""
	@echo "For wallet-specific commands, see: wallet-example/Makefile"

# Main build command - handles cyclic dependency automatically
# Note: Uses 'install' which runs unit tests but not integration tests
# Use 'install-all-tests' for full test coverage including integration tests
install: build-core build-shared build-reactor
	@echo "✓ Build complete! All modules installed to local repository."

# Full build with all tests including integration tests (for CI)
install-all-tests: build-core build-shared build-reactor-verify
	@echo "✓ Build complete with all tests! All modules installed to local repository."

# CI build - verifies build, only installs minimal modules needed
# 1. Install crablet-eventstore (needed by shared-examples-domain)
# 2. Install shared-examples-domain (needed by reactor modules in test scope)
# 3. Verify reactor (no install needed for reactor modules)
ci-verify: build-core build-shared build-reactor-verify
	@echo "✓ CI build complete with all tests! (only crablet-eventstore and shared-examples-domain installed)"

# Alias for install
build-all: install

# Build crablet-eventstore without tests (step 1 of cyclic dependency resolution)
build-core:
	@echo "Building crablet-eventstore (main code only, skipping tests)..."
	@./mvnw clean install -pl crablet-eventstore -am -DskipTests

# Build shared-examples-domain (step 2 - depends on crablet-eventstore)
build-shared:
	@echo "Building shared-examples-domain..."
	@cd shared-examples-domain && ../mvnw install


# Build all reactor modules with tests (step 3 - shared-examples-domain is now available)
build-reactor:
	@echo "Building reactor modules (with tests)..."
	@./mvnw install

# Build all reactor modules with all tests including integration tests (for CI)
build-reactor-verify:
	@echo "Building reactor modules (with all tests including integration tests)..."
	@./mvnw verify

# Compile all modules
compile: build-core build-shared
	@./mvnw compile

# Package all modules
package: build-core build-shared
	@./mvnw package

# Run tests (requires build-core and build-shared first)
test: build-core build-shared
	@./mvnw test

# Build without tests
test-skip: build-core build-shared
	@./mvnw install -DskipTests

# Full verify (clean build with tests)
verify: build-core build-shared
	@./mvnw verify

# Clean all build artifacts
clean:
	@./mvnw clean
	@cd shared-examples-domain && ../mvnw clean

# Application commands
start:
	cd wallet-example && $(MAKE) start

wallet-dev:
	cd wallet-example && $(MAKE) dev
