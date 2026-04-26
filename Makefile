# Crablet Multi-Module Project Makefile
# Provides convenient commands for working with the multi-module structure
#
# Build order (required due to cross-module test dependencies):
# 1. crablet-eventstore    (no tests — needed by crablet-test-support)
# 2. crablet-test-support  (test utilities + DB migrations for all modules)
# 3. crablet-commands       (no tests — needed by shared-examples-domain)
# 4. shared-examples-domain (wallet/course/notification examples, used by reactor in test scope)
# 5. reactor               (all framework modules with full tests)
#
# wallet-example-app is built separately after the reactor is installed.
# See BUILD.md for full explanation.

.PHONY: help install install-all-tests ci-verify build-all compile package test test-skip clean verify build-core build-shared build-reactor build-reactor-verify start wallet-dev docs-check docs-compile-check docs-generate docs-generate-check codegen-build codegen-install codegen-plan-example codegen-check

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
	@echo "  docs-check       - Validate markdown links and key documentation guardrails"
	@echo "  docs-compile-check - Compile tutorial fixture sources in docs-samples"
	@echo "  docs-generate    - Regenerate source-derived snippets under docs/user/generated"
	@echo "  docs-generate-check - Fail if generated snippets are out of date"
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
	@echo "  build-command - Build crablet-commands without tests and install"
	@echo "  build-shared - Build shared-examples-domain and install"
	@echo "  build-reactor - Build all reactor modules (after core, command and shared are installed)"
	@echo ""
	@echo "Application Commands:"
	@echo "  start       - Start wallet-example-app application"
	@echo "  wallet-dev  - Start wallet development environment (alias for start)"
	@echo ""
	@echo "Note: wallet-example-app is a Spring Boot application - use 'mvn spring-boot:run' in wallet-example-app directory"
	@echo ""
	@echo "Codegen Commands (run after 'make install'):"
	@echo "  codegen-build   - Build embabel-codegen fat JAR (embabel-codegen/target/embabel-codegen.jar)"
	@echo "  codegen-install - Build and install embabel-codegen to local Maven repo"
	@echo "  codegen-plan-example - Print planned artifacts for the documented loan feature slice"
	@echo "  codegen-check   - Run embabel-codegen tests and planner smoke check"

# Main build command - handles cyclic dependency automatically
# Note: Uses 'install' which runs unit tests but not integration tests
# Use 'install-all-tests' for full test coverage including integration tests
install: build-core build-test-support build-command build-shared build-reactor
	@echo "✓ Build complete! All modules installed to local repository."

# Full build with all tests including integration tests (for CI)
install-all-tests: build-core build-test-support build-command build-shared build-reactor-verify
	@echo "✓ Build complete with all tests! All modules installed to local repository."

# CI build - verifies build, only installs minimal modules needed
# 1. Install crablet-eventstore (needed by crablet-test-support)
# 2. Install crablet-test-support (needed by shared-examples-domain)
# 3. Install shared-examples-domain (needed by reactor modules in test scope)
# 4. Verify reactor (no install needed for reactor modules)
ci-verify: build-core build-test-support build-command build-shared build-reactor-verify
	@echo "✓ CI build complete with all tests!"

docs-check:
	@chmod +x scripts/verify-docs.sh
	@./scripts/verify-docs.sh

docs-compile-check:
	@./mvnw -pl docs-samples -am compile -DskipTests

docs-generate:
	@chmod +x scripts/generate-doc-snippets.sh
	@./scripts/generate-doc-snippets.sh write

docs-generate-check:
	@chmod +x scripts/generate-doc-snippets.sh
	@tmp_dir="$$(mktemp -d)"; \
	./scripts/generate-doc-snippets.sh check "$$tmp_dir"; \
	rm -rf "$$tmp_dir"

# Alias for install
build-all: install

# Build crablet-eventstore without tests (step 1 of cyclic dependency resolution)
# Note: Install parent POM, then minimal stubs for modules to break circular dependency
# Then build crablet-eventstore with tests skipped (will replace the stub)
# -am removed because shared-examples-domain is not in reactor and would cause build to fail
build-core:
	@echo "Installing parent POM and minimal stubs to break circular dependency..."
	@./mvnw install -N -q 2>/dev/null || true
	@mkdir -p /tmp/crablet-stub && touch /tmp/crablet-stub/empty.jar
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-eventstore -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-eventstore/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-eventstore -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -Dclassifier=tests -DpomFile=crablet-eventstore/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-test-support -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-test-support/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-commands -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-commands/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-automations -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-automations/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=shared-examples-domain -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=shared-examples-domain/pom.xml -q 2>/dev/null || true
	@echo "Building crablet-eventstore (main code only, skipping tests)..."
	@./mvnw clean compile package install -pl crablet-eventstore -DskipTests -Dmaven.test.skip=true

# Build crablet-test-support (step 2 - test utilities that depend on crablet-eventstore)
build-test-support:
	@echo "Building crablet-test-support..."
	@cd crablet-test-support && ../mvnw install

# Build crablet-commands without tests (step 2 - needed by shared-examples-domain)
build-command:
	@echo "Building crablet-commands (main code only, skipping tests)..."
	@./mvnw clean compile package install -pl crablet-commands -DskipTests -Dmaven.test.skip=true

# Build shared-examples-domain (step 3 - depends on crablet-eventstore and crablet-commands)
build-shared:
	@echo "Building shared-examples-domain..."
	@cd shared-examples-domain && ../mvnw install
	@echo "Building crablet-eventstore test-jar (needed by crablet-commands tests)..."
	@./mvnw test-compile package install -pl crablet-eventstore -DskipTests


# Build all reactor modules with tests (step 4 - shared-examples-domain is now available)
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
compile: build-core build-command build-shared
	@./mvnw compile

# Package all modules
package: build-core build-command build-shared
	@./mvnw package

# Run tests (requires core, test-support, commands and shared examples first)
test: build-core build-test-support build-command build-shared
	@./mvnw test

# Build without tests
test-skip: build-core build-command build-shared
	@./mvnw install -DskipTests

# Full verify (clean build with tests)
verify: build-core build-command build-shared
	@./mvnw verify

# Clean all build artifacts
clean:
	@./mvnw clean
	@cd shared-examples-domain && ../mvnw clean

# Application commands
start:
	cd wallet-example-app && ../mvnw spring-boot:run

wallet-dev:
	cd wallet-example-app && ../mvnw spring-boot:run

# Codegen — excluded from reactor, build separately after 'make install'
codegen-build:
	@echo "Building embabel-codegen fat JAR..."
	@cd embabel-codegen && ../mvnw package -DskipTests
	@echo "✓ JAR: embabel-codegen/target/embabel-codegen.jar"

codegen-install:
	@echo "Installing embabel-codegen to local Maven repo..."
	@cd embabel-codegen && ../mvnw install -DskipTests
	@echo "✓ embabel-codegen installed"

codegen-plan-example: codegen-build
	@echo "Planning documented loan feature slice..."
	@cd embabel-codegen && java -jar target/embabel-codegen.jar plan --model ../docs/examples/loan-submit-feature-slice-event-model.yaml

codegen-check:
	@echo "Running embabel-codegen tests..."
	@cd embabel-codegen && ../mvnw test
	@$(MAKE) codegen-plan-example
