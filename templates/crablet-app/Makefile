CRABLET_CODEGEN_JAR ?= tools/crablet-codegen.jar

.PHONY: help plan generate sync-scenarios k8s diagram-preview verify check

help:
	@echo "Crablet App Template"
	@echo ""
	@echo "Commands:"
	@echo "  plan            - Print planned artifacts from event-model.yaml"
	@echo "  generate        - Generate structural Crablet code from event-model.yaml"
	@echo "  sync-scenarios  - Report drift between event-model.yaml scenarios and test scaffolds"
	@echo "  k8s             - Generate Kubernetes manifests (k8s/base) from event-model.yaml"
	@echo "  diagram-preview - Write diagram-preview.html from event-model.yaml"
	@echo "  verify          - Run Maven verification"
	@echo "  check           - Run plan and verify"
	@echo ""
	@echo "Set CRABLET_CODEGEN_JAR=/path/to/crablet-codegen.jar if needed."

plan:
	@java -jar $(CRABLET_CODEGEN_JAR) plan --model event-model.yaml

generate:
	@java -jar $(CRABLET_CODEGEN_JAR) generate --model event-model.yaml --output src/main/java

sync-scenarios:
	@java -jar $(CRABLET_CODEGEN_JAR) sync-scenarios --model event-model.yaml --output src/main/java

k8s:
	@java -jar $(CRABLET_CODEGEN_JAR) k8s --model event-model.yaml --output .

diagram-preview:
	@command -v node >/dev/null 2>&1 || (echo "error: Node.js is required. Install from https://nodejs.org" && exit 1)
	@[ -d tools/node_modules/js-yaml ] || npm install --prefix tools --silent
	@node tools/diagram-preview.js

verify:
	@./mvnw verify

check: plan verify
