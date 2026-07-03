# Crablet Multi-Module Project Makefile
# Provides convenient commands for working with the multi-module structure
#
# Build order (required due to cross-module test dependencies):
# 1. crablet-observability + crablet-eventstore (no tests — needed by crablet-test-support)
# 2. crablet-test-support                       (test utilities + DB migrations for all modules)
# 3. crablet-commands                            (no tests — needed by shared-examples-domain)
# 4. shared-examples-domain                      (wallet/course/notification examples, used by reactor in test scope)
# 5. reactor                                    (all framework modules with full tests)
#
# examples/wallet-example-app is built separately after the reactor is installed.
# See BUILD.md for full explanation.

.PHONY: help install install-all-tests ci-verify validate-all skills-check check-test-support-artifact check-db-migrations-artifact check-migration-sync build-all compile package test test-pl test-skip examples-check clean verify build-core build-shared build-test-commands build-reactor build-reactor-verify build-reactor-install-artifacts start wallet-dev course-start course-dev serve-docs docs-check docs-compile-check docs-generate docs-generate-check codegen-build codegen-install codegen-plan-example codegen-check codegen-snapshot-verify codegen-regenerate-verify event-model-diagrams

.NOTPARALLEL:

# Default target
help:
	@echo "Crablet Multi-Module Project"
	@echo ""
	@echo "Build Commands:"
	@echo "  install          - Build and install all modules (handles cyclic dependencies automatically)"
	@echo "  install-all-tests - Build with all tests including integration tests (installs to local repo)"
	@echo "  ci-verify        - Build with all tests for CI (no local repo install, faster)"
	@echo "  validate-all     - Full local validation: framework, docs, codegen, examples, and template skills"
	@echo "  skills-check     - Verify templates/crablet-app skill files and CLAUDE routing"
	@echo "  check-test-support-artifact  - Verify installed crablet-test-support jar matches source migrations"
	@echo "  check-db-migrations-artifact - Verify installed crablet-db-migrations jar matches source migrations"
	@echo "  check-migration-sync - Verify migration sources match between db-migrations and test-support"
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
	@echo "  test-pl     - Run tests for one reactor module + deps (use PL=...; avoids stale ~/.m2 siblings)"
	@echo "  test-skip   - Build without running tests (faster)"
	@echo "  examples-check - Test standalone example applications"
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
	@echo "  start         - Start examples/wallet-example-app application"
	@echo "  wallet-dev    - Start wallet development environment (alias for start)"
	@echo "  course-start  - Start examples/course-example-app application"
	@echo "  course-dev    - Start course development environment (alias for course-start)"
	@echo "  serve-docs         - Serve the GitHub Pages site locally at http://localhost:8091"
	@echo "  event-model-diagrams - Generate event-model SVGs (requires playwright + js-yaml npm packages)"
	@echo ""
	@echo "Note: wallet-example-app is a Spring Boot application - use 'mvn spring-boot:run' in examples/wallet-example-app directory"
	@echo ""
	@echo "Codegen Commands (run after 'make install'):"
	@echo "  codegen-build   - Build crablet-codegen fat JAR (crablet-codegen/target/crablet-codegen.jar)"
	@echo "  codegen-install - Build and install crablet-codegen to local Maven repo"
	@echo "  codegen-plan-example - Print planned artifacts for the documented loan feature slice"
	@echo "  codegen-check          - Run crablet-codegen tests and planner smoke check"
	@echo "  codegen-regenerate-verify - Regenerate loan slice and diff against committed snapshot"

# Main build command - handles cyclic dependency automatically
# Note: Uses 'install' which runs unit tests but not integration tests
# Use 'install-all-tests' for full test coverage including integration tests
install: build-core build-test-support check-test-support-artifact check-db-migrations-artifact check-migration-sync build-command build-shared build-test-commands build-reactor
	@echo "✓ Build complete! All modules installed to local repository."

# Full build with all tests including integration tests (for CI)
install-all-tests: build-core build-test-support check-test-support-artifact check-db-migrations-artifact check-migration-sync build-command build-shared build-test-commands build-reactor-verify build-reactor-install-artifacts
	@echo "✓ Build complete with all tests! All modules installed to local repository."

# CI build - verifies build, only installs minimal modules needed
# 1. Install crablet-eventstore (needed by crablet-test-support)
# 2. Install crablet-test-support (needed by shared-examples-domain)
# 3. Install shared-examples-domain (needed by reactor modules in test scope)
# 4. Verify reactor (no install needed for reactor modules)
ci-verify: build-core build-test-support check-test-support-artifact check-db-migrations-artifact check-migration-sync build-command build-shared build-test-commands build-reactor-verify
	@echo "✓ CI build complete with all tests!"

validate-all: skills-check install-all-tests docs-check docs-compile-check docs-generate-check codegen-check codegen-regenerate-verify codegen-snapshot-verify examples-check
	@echo "✓ Full local validation complete."

skills-check:
	@chmod +x scripts/check-template-skills.sh
	@./scripts/check-template-skills.sh

check-test-support-artifact:
	@chmod +x scripts/check-test-support-artifact.sh
	@./scripts/check-test-support-artifact.sh

check-db-migrations-artifact:
	@chmod +x scripts/check-db-migrations-artifact.sh
	@./scripts/check-db-migrations-artifact.sh

check-migration-sync:
	@diff -r crablet-db-migrations/src/main/resources/db/migration/ crablet-test-support/src/main/resources/db/migration/


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
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-observability -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-observability/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-test-support -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-test-support/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-commands -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-commands/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-test-commands -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-test-commands/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=crablet-automations -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=crablet-automations/pom.xml -q 2>/dev/null || true
	@./mvnw install:install-file -Dfile=/tmp/crablet-stub/empty.jar -DgroupId=com.crablet -DartifactId=shared-examples-domain -Dversion=1.0.0-SNAPSHOT -Dpackaging=jar -DpomFile=shared-examples-domain/pom.xml -q 2>/dev/null || true
	@echo "Building crablet-db-migrations (clean install — guards against stale SQL files in target/)..."
	@./mvnw clean install -pl crablet-db-migrations -DskipTests -q
	@echo "Building crablet-observability and crablet-eventstore (main code only, skipping tests)..."
	@./mvnw clean compile package install -pl crablet-observability,crablet-eventstore -DskipTests -Dmaven.test.skip=true

# Build crablet-test-support (step 2 - test utilities that depend on crablet-eventstore)
build-test-support:
	@echo "Building crablet-test-support..."
	@cd crablet-test-support && ../mvnw clean install

# Build crablet-commands without tests (step 2 - needed by shared-examples-domain)
build-command:
	@echo "Building crablet-commands (main code only, skipping tests)..."
	@./mvnw clean compile package install -pl crablet-commands -DskipTests -Dmaven.test.skip=true

# Build crablet-test-commands (fast in-memory handler BDD base; depends on crablet-commands main + crablet-test-support)
build-test-commands:
	@echo "Building crablet-test-commands..."
	@cd crablet-test-commands && ../mvnw clean install

# Build shared-examples-domain (step 3 - depends on crablet-eventstore and crablet-commands)
build-shared: check-test-support-artifact
	@echo "Building shared-examples-domain..."
	@cd shared-examples-domain && ../mvnw install
	@echo "Building crablet-eventstore test-jar (needed by crablet-commands tests)..."
	@./mvnw test-compile package install -pl crablet-eventstore -DskipTests


# Build all reactor modules with tests (step 4 - shared-examples-domain is now available)
build-reactor: check-test-support-artifact
	@echo "Building reactor modules (with tests)..."
	@./mvnw install

# Build all reactor modules with all tests including integration tests (for CI)
# Note: Don't clean - build-core and build-shared already installed artifacts to local repo
# The reactor build will use installed versions and compile fresh for tests
build-reactor-verify: check-test-support-artifact
	@echo "Building reactor modules (with all tests including integration tests)..."
	@./mvnw verify -Dmaven.clean.skip=true

# Install reactor artifacts after verification so standalone examples resolve real jars, not bootstrap stubs.
build-reactor-install-artifacts: check-test-support-artifact
	@echo "Installing verified reactor artifacts..."
	@./mvnw install -DskipTests -Dmaven.test.skip=true -Dmaven.clean.skip=true

# Compile all modules
compile: build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw compile

# Package all modules
package: build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw package

# Run tests (requires core, test-support, commands and shared examples first)
test: build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw test

# Run tests for a subset of the reactor, including dependent modules in the same build (-am).
# Ensures sibling modules (e.g. crablet-eventstore) are rebuilt instead of a stale ~/.m2 SNAPSHOT.
# Example: make test-pl PL=crablet-commands
# Optional: extra Maven args, e.g. make test-pl PL=crablet-commands MVN_ARGS='-Dtest=FooTest'
test-pl:
	@test -n "$(PL)" || (echo "Usage: make test-pl PL=<module-dir>  Example: make test-pl PL=crablet-commands"; exit 1)
	@$(MAKE) build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw test -pl $(PL) -am $(MVN_ARGS)

# Build without tests
test-skip: build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw install -DskipTests

examples-check:
	@echo "Testing wallet example app..."
	@cd examples/wallet-example-app && ../../mvnw clean test
	@echo "Testing course example app..."
	@cd examples/course-example-app && ../../mvnw clean test
	@echo "✓ Example apps tested."

# Full verify (clean build with tests)
verify: build-core build-test-support check-test-support-artifact build-command build-shared build-test-commands
	@./mvnw verify

# Clean all build artifacts
clean:
	@./mvnw clean
	@cd shared-examples-domain && ../mvnw clean

# Application commands
start:
	cd examples/wallet-example-app && ../../mvnw spring-boot:run

wallet-dev:
	cd examples/wallet-example-app && ../../mvnw spring-boot:run

course-start:
	cd examples/course-example-app && ../../mvnw spring-boot:run

course-dev:
	cd examples/course-example-app && ../../mvnw spring-boot:run

serve-docs:
	@echo "Serving docs site at http://localhost:8091"
	@echo "Open: http://localhost:8091/index.html"
	cd docs && python3 -m http.server 8091

event-model-diagrams:
	node .github/scripts/generate-event-model-svgs.js

# Codegen — excluded from reactor, build separately after 'make install'
codegen-build:
	@echo "Building crablet-codegen fat JAR..."
	@cd crablet-codegen && ../mvnw package -DskipTests
	@echo "✓ JAR: crablet-codegen/target/crablet-codegen.jar"

codegen-install:
	@echo "Installing crablet-codegen to local Maven repo..."
	@cd crablet-codegen && ../mvnw install -DskipTests
	@echo "✓ crablet-codegen installed"

codegen-plan-example: codegen-build
	@echo "Planning documented loan feature slice..."
	@cd crablet-codegen && java -jar target/crablet-codegen.jar plan --model ../docs/user/examples/loan-submit-feature-slice-event-model.yaml

codegen-check:
	@echo "Running crablet-codegen tests..."
	@cd crablet-codegen && ../mvnw test
	@$(MAKE) codegen-plan-example

codegen-snapshot-verify:
	@echo "Installing reactor artifacts (loan snapshot needs crablet-commands-web/views; metrics-micrometer needs the commands test-jar)..."
	@./mvnw install -DskipTests -Dmaven.clean.skip=true
	@echo "Verifying committed loan-slice generated snapshot..."
	@./mvnw verify -f examples/loan-generated-snapshot/pom.xml -DskipTests=false

# Regenerate machine-owned files from the loan fixture and diff them against the committed snapshot.
# Diff paths match the ownership table in docs/dev/plans/ai-workflow-trust-hardening.md.
# Normalization: none required — files end with a single newline from the generator.
codegen-regenerate-verify: codegen-build
	@echo "Regenerating loan slice into temp dir and diffing against committed snapshot..."
	@REGEN_DIR=$$(mktemp -d) && \
	SNAPSHOT=examples/loan-generated-snapshot && \
	JAVA_OUT=$$REGEN_DIR/src/main/java && \
	mkdir -p $$JAVA_OUT && \
	(cd crablet-codegen && java -jar target/crablet-codegen.jar generate \
	  --model ../docs/user/examples/loan-submit-feature-slice-event-model.yaml \
	  --output $$JAVA_OUT 2>/dev/null; true) && \
	FAIL=0 && \
	for REL_PATH in \
	  com/example/loan/domain/LoanApplicationEvent.java \
	  com/example/loan/domain/LoanApplicationSubmitted.java \
	  com/example/loan/command/SubmitLoanApplication.java \
	  com/example/loan/command/SubmitLoanApplicationCommandHandler.java \
	  com/example/loan/view/PendingLoanApplicationsViewProjector.java; do \
	  COMMITTED=$$SNAPSHOT/src/main/java/$$REL_PATH; \
	  REGENERATED=$$JAVA_OUT/$$REL_PATH; \
	  if ! diff -q $$COMMITTED $$REGENERATED > /dev/null 2>&1; then \
	    echo "DRIFT: $$REL_PATH"; \
	    diff $$COMMITTED $$REGENERATED || true; \
	    FAIL=1; \
	  fi; \
	done && \
	SQL_COMMITTED=$$SNAPSHOT/src/main/resources/db/migration/V100__create_pending_loan_applications.sql && \
	SQL_REGEN=$$REGEN_DIR/src/main/resources/db/migration/V100__create_pending_loan_applications.sql && \
	if ! diff -q $$SQL_COMMITTED $$SQL_REGEN > /dev/null 2>&1; then \
	  echo "DRIFT: db/migration/V100__create_pending_loan_applications.sql"; \
	  diff $$SQL_COMMITTED $$SQL_REGEN || true; \
	  FAIL=1; \
	fi && \
	rm -rf $$REGEN_DIR && \
	if [ $$FAIL -eq 1 ]; then \
	  echo ""; \
	  echo "Generator drift detected. Refresh the snapshot: run 'make codegen-build' then regenerate and commit the machine-owned files."; \
	  exit 1; \
	else \
	  echo "✓ All machine-owned loan snapshot files match the regenerated output"; \
	fi
