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
	@echo "  start       - Start wallet-example-app application"
	@echo "  wallet-dev  - Start wallet development environment (alias for start)"
	@echo ""
	@echo "Note: wallet-example-app is a Spring Boot application - use 'mvn spring-boot:run' in wallet-example-app directory"

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
# Note: Install parent POM, then minimal stubs for both modules to break circular dependency
# Then build crablet-eventstore with tests skipped (will replace the stub)
# -am removed because shared-examples-domain is not in reactor and would cause build to fail
build-core:
	@echo "Installing parent POM and minimal stubs to break circular dependency..."
	@./mvnw install -N -q 2>/dev/null || true
	@mkdir -p /tmp/crablet-stub && touch /tmp/crablet-stub/empty.jar
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-eventstore -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-eventstore/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=shared-examples-domain -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=shared-examples-domain/pom.xml -q 2>/dev/null || true
	@echo "Building crablet-eventstore (main code only, skipping tests)..."
	@./mvnw clean compile package install -pl crablet-eventstore -DskipTests -Dmaven.test.skip=true

# Build shared-examples-domain (step 2 - depends on crablet-eventstore)
build-shared:
	@echo "Building shared-examples-domain..."
	@cd shared-examples-domain && ../mvnw install


# Build all reactor modules with tests (step 3 - shared-examples-domain is now available)
build-reactor:
	@echo "Building reactor modules (with tests)..."
	@./mvnw install

# Build all reactor modules with all tests including integration tests (for CI)
# Note: Don't clean - build-core and build-shared already installed artifacts to local repo
# The reactor build will use installed versions and compile fresh for tests
build-reactor-verify:
	@echo "Building reactor modules (with all tests including integration tests)..."
	@./mvnw verify -Dmaven.clean.skip=true

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
	cd wallet-example-app && ./mvnw spring-boot:run

wallet-dev:
	cd wallet-example-app && ./mvnw spring-boot:run
