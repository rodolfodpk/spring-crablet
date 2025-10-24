# Crablet Multi-Module Project Makefile
# Provides convenient commands for working with the multi-module structure

.PHONY: help install test clean start wallet-dev

# Default target
help:
	@echo "Crablet Multi-Module Project"
	@echo ""
	@echo "Commands:"
	@echo "  install     - Build and install all modules"
	@echo "  test        - Run all tests across all modules"
	@echo "  clean       - Clean all build artifacts"
	@echo "  start       - Start wallet-example application"
	@echo "  wallet-dev  - Start wallet development environment (alias for start)"
	@echo ""
	@echo "For wallet-specific commands, see: wallet-example/Makefile"

install:
	mvn clean install

test:
	mvn test

clean:
	mvn clean

start:
	cd wallet-example && $(MAKE) start

wallet-dev:
	cd wallet-example && $(MAKE) dev
