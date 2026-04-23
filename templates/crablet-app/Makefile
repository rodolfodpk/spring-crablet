CRABLET_CODEGEN_JAR ?= tools/embabel-codegen.jar

.PHONY: help plan generate verify check

help:
	@echo "Crablet App Template"
	@echo ""
	@echo "Commands:"
	@echo "  plan     - Print planned artifacts from event-model.yaml"
	@echo "  generate - Generate structural Crablet code from event-model.yaml"
	@echo "  verify   - Run Maven verification"
	@echo "  check    - Run plan and verify"
	@echo ""
	@echo "Set CRABLET_CODEGEN_JAR=/path/to/embabel-codegen.jar if needed."

plan:
	@java -jar $(CRABLET_CODEGEN_JAR) plan --model event-model.yaml

generate:
	@java -jar $(CRABLET_CODEGEN_JAR) generate --model event-model.yaml --output src/main/java

verify:
	@./mvnw verify

check: plan verify
